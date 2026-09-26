package br.pucgoias.ads.delivery.servico;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.group;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.match;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.project;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.sort;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.bson.types.Decimal128;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.mongodb.client.result.UpdateResult;

import br.pucgoias.ads.delivery.dominio.Cliente;
import br.pucgoias.ads.delivery.dominio.FaturamentoRestaurante;
import br.pucgoias.ads.delivery.dominio.ItemCardapio;
import br.pucgoias.ads.delivery.dominio.ItemPedido;
import br.pucgoias.ads.delivery.dominio.Pedido;
import br.pucgoias.ads.delivery.dominio.Restaurante;
import br.pucgoias.ads.delivery.dominio.StatusPedido;
import br.pucgoias.ads.delivery.excecao.ItemDuplicadoException;
import br.pucgoias.ads.delivery.excecao.ItemIndisponivelException;
import br.pucgoias.ads.delivery.excecao.PedidoInvalidoException;
import br.pucgoias.ads.delivery.excecao.RecursoNaoEncontradoException;
import br.pucgoias.ads.delivery.excecao.RestauranteDuplicadoException;
import br.pucgoias.ads.delivery.repositorio.PedidoRepository;
import br.pucgoias.ads.delivery.repositorio.RestauranteRepository;

@Service
public class DeliveryService {

    private final RestauranteRepository restauranteRepository;
    private final PedidoRepository pedidoRepository;
    private final MongoTemplate mongoTemplate;

    public DeliveryService(RestauranteRepository restauranteRepository,
                           PedidoRepository pedidoRepository,
                           MongoTemplate mongoTemplate) {
        this.restauranteRepository = restauranteRepository;
        this.pedidoRepository = pedidoRepository;
        this.mongoTemplate = mongoTemplate;
    }

    public Restaurante cadastrarRestaurante(Restaurante restaurante) {
        try {
            return restauranteRepository.save(restaurante);
        } catch (DuplicateKeyException e) {
            throw new RestauranteDuplicadoException(restaurante.getNome());
        }
    }

    public Restaurante buscarRestaurante(String id) {
        return restauranteRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Restaurante", id));
    }

    public Pedido buscarPedido(String id) {
        return pedidoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pedido", id));
    }

    /**
     * Altera o preco de um item do cardapio com o operador posicional "$":
     * { $set: { "cardapio.$.preco": novoPreco } }
     */
    public void alterarPreco(String restauranteId, String codigo, BigDecimal novoPreco) {
        Query query = Query.query(Criteria.where("id").is(restauranteId)
                .and("cardapio.codigo").is(codigo));
        Update update = new Update().set("cardapio.$.preco", new Decimal128(novoPreco));
        UpdateResult resultado = mongoTemplate.updateFirst(query, update, Restaurante.class);
        if (resultado.getMatchedCount() == 0) {
            throw new RecursoNaoEncontradoException("Item " + codigo + " do restaurante", restauranteId);
        }
    }

    public void adicionarItemCardapio(String restauranteId, ItemCardapio item) {
        Query query = Query.query(Criteria.where("id").is(restauranteId)
                .and("cardapio.codigo").ne(item.codigo()));
        Update update = new Update().push("cardapio", item);
        UpdateResult resultado = mongoTemplate.updateFirst(query, update, Restaurante.class);
        if (resultado.getMatchedCount() == 0) {
            if (!restauranteRepository.existsById(restauranteId)) {
                throw new RecursoNaoEncontradoException("Restaurante", restauranteId);
            }
            throw new ItemDuplicadoException(item.codigo());
        }
    }

    public Pedido criarPedido(String restauranteId, Cliente cliente, List<ItemSolicitado> solicitados) {
        if (solicitados == null || solicitados.isEmpty()) {
            throw new PedidoInvalidoException("lista de itens vazia");
        }
        for (ItemSolicitado solicitado : solicitados) {
            if (solicitado.quantidade() <= 0) {
                throw new PedidoInvalidoException("quantidade deve ser maior que zero");
            }
        }

        Restaurante restaurante = buscarRestaurante(restauranteId);

        List<ItemPedido> itens = new ArrayList<>();
        for (ItemSolicitado solicitado : solicitados) {
            ItemCardapio itemCardapio = restaurante.buscarItem(solicitado.codigo())
                    .filter(ItemCardapio::disponivel)
                    .orElseThrow(() -> new ItemIndisponivelException(solicitado.codigo()));
            itens.add(new ItemPedido(itemCardapio.codigo(), itemCardapio.nome(),
                    itemCardapio.preco(), solicitado.quantidade()));
        }

        Pedido pedido = new Pedido(restauranteId, cliente, itens);
        return pedidoRepository.save(pedido);
    }

    public Pedido avancarStatus(String pedidoId) {
        Pedido pedido = buscarPedido(pedidoId);
        pedido.avancarStatus();
        return pedidoRepository.save(pedido);
    }

    public Pedido cancelarPedido(String pedidoId) {
        Pedido pedido = buscarPedido(pedidoId);
        pedido.cancelar();
        return pedidoRepository.save(pedido);
    }

    public List<FaturamentoRestaurante> faturamentoPorRestaurante() {
        Aggregation agregacao = newAggregation(
                match(Criteria.where("status").is(StatusPedido.ENTREGUE)),
                group("restauranteId")
                        .sum("total").as("faturamento")
                        .count().as("quantidadePedidos"),
                project("faturamento", "quantidadePedidos").and("restauranteId").previousOperation(),
                sort(Sort.Direction.DESC, "faturamento"));

        return mongoTemplate.aggregate(agregacao, "pedidos", FaturamentoRestaurante.class)
                .getMappedResults();
    }
}
