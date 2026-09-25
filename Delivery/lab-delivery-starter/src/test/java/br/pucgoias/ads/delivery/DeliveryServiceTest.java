package br.pucgoias.ads.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.context.annotation.Import;

import br.pucgoias.ads.delivery.dominio.Cliente;
import br.pucgoias.ads.delivery.dominio.Endereco;
import br.pucgoias.ads.delivery.dominio.FaturamentoRestaurante;
import br.pucgoias.ads.delivery.dominio.ItemCardapio;
import br.pucgoias.ads.delivery.dominio.Pedido;
import br.pucgoias.ads.delivery.dominio.Restaurante;
import br.pucgoias.ads.delivery.dominio.StatusPedido;
import br.pucgoias.ads.delivery.excecao.ItemDuplicadoException;
import br.pucgoias.ads.delivery.excecao.ItemIndisponivelException;
import br.pucgoias.ads.delivery.excecao.PedidoInvalidoException;
import br.pucgoias.ads.delivery.excecao.RestauranteDuplicadoException;
import br.pucgoias.ads.delivery.excecao.TransicaoInvalidaException;
import br.pucgoias.ads.delivery.repositorio.PedidoRepository;
import br.pucgoias.ads.delivery.repositorio.RestauranteRepository;
import br.pucgoias.ads.delivery.servico.DeliveryService;
import br.pucgoias.ads.delivery.servico.ItemSolicitado;

@DataMongoTest
@Import(DeliveryService.class)
class DeliveryServiceTest {

    @Autowired DeliveryService servico;
    @Autowired RestauranteRepository restauranteRepository;
    @Autowired PedidoRepository pedidoRepository;

    private Restaurante cerrado;
    private final Cliente ana = new Cliente("Ana Souza", "62999990001");

    @BeforeEach
    void preparar() {
        pedidoRepository.deleteAll();
        restauranteRepository.deleteAll();
        Restaurante r = new Restaurante("Sabor do Cerrado", "Goiana",
                new Endereco("Rua T-27, 100", "Setor Bueno", "Goiania"));
        r.incluirNoCardapio(new ItemCardapio("PAMONHA", "Pamonha de sal", new BigDecimal("12.00"), true));
        r.incluirNoCardapio(new ItemCardapio("EMPADAO", "Empadao goiano", new BigDecimal("25.50"), true));
        r.incluirNoCardapio(new ItemCardapio("PEQUI", "Arroz com pequi", new BigDecimal("32.00"), false));
        cerrado = servico.cadastrarRestaurante(r);
    }

    private Pedido pedidoEntregue(String restauranteId, String codigo, int quantidade) {
        Pedido p = servico.criarPedido(restauranteId, ana, List.of(new ItemSolicitado(codigo, quantidade)));
        servico.avancarStatus(p.getId());
        servico.avancarStatus(p.getId());
        return servico.avancarStatus(p.getId());
    }

    @Test
    @DisplayName("1. Cadastro gera identificador e persiste o cardapio incorporado")
    void caso1_cadastro() {
        Restaurante lido = servico.buscarRestaurante(cerrado.getId());
        assertThat(lido.getId()).isNotBlank();
        assertThat(lido.getCardapio()).hasSize(3);
        assertThat(lido.buscarItem("EMPADAO").orElseThrow().preco()).isEqualByComparingTo("25.50");
    }

    @Test
    @DisplayName("2. Nome de restaurante duplicado e rejeitado (R1)")
    void caso2_nomeDuplicado() {
        Restaurante copia = new Restaurante("Sabor do Cerrado", "Goiana",
                new Endereco("Av. 85, 50", "Setor Marista", "Goiania"));
        assertThatThrownBy(() -> servico.cadastrarRestaurante(copia))
                .isInstanceOf(RestauranteDuplicadoException.class)
                .hasMessageContaining("Sabor do Cerrado");
    }

    @Test
    @DisplayName("3. Busca por categoria ignora maiusculas e minusculas (R5)")
    void caso3_categoria() {
        assertThat(restauranteRepository.findByCategoriaIgnoreCase("goiana"))
                .extracting(Restaurante::getNome).containsExactly("Sabor do Cerrado");
    }

    @Test
    @DisplayName("4. Busca por bairro consulta campo do objeto incorporado (R5)")
    void caso4_bairro() {
        servico.cadastrarRestaurante(new Restaurante("Nonna Pasta", "Italiana",
                new Endereco("Rua 9, 20", "Setor Oeste", "Goiania")));
        assertThat(restauranteRepository.findByEnderecoBairro("Setor Oeste"))
                .extracting(Restaurante::getNome).containsExactly("Nonna Pasta");
    }


    @Test
    @DisplayName("10. Faturamento agrega apenas pedidos ENTREGUE, em ordem decrescente (R7)")
    void caso10_faturamento() {
        Restaurante nonna = servico.cadastrarRestaurante(new Restaurante("Nonna Pasta", "Italiana",
                new Endereco("Rua 9, 20", "Setor Oeste", "Goiania")));
        servico.adicionarItemCardapio(nonna.getId(),
                new ItemCardapio("LASANHA", "Lasanha bolonhesa", new BigDecimal("48.00"), true));

        pedidoEntregue(cerrado.getId(), "PAMONHA", 2); 
        pedidoEntregue(cerrado.getId(), "EMPADAO", 1);   
        pedidoEntregue(nonna.getId(), "LASANHA", 2);     
        servico.criarPedido(nonna.getId(), ana, List.of(new ItemSolicitado("LASANHA", 5))); // nao entregue

        List<FaturamentoRestaurante> relatorio = servico.faturamentoPorRestaurante();

        assertThat(relatorio).hasSize(2);
        assertThat(relatorio.get(0).restauranteId()).isEqualTo(nonna.getId());
        assertThat(relatorio.get(0).faturamento()).isEqualByComparingTo("96.00");
        assertThat(relatorio.get(0).quantidadePedidos()).isEqualTo(1);
        assertThat(relatorio.get(1).restauranteId()).isEqualTo(cerrado.getId());
        assertThat(relatorio.get(1).faturamento()).isEqualByComparingTo("49.50");
        assertThat(relatorio.get(1).quantidadePedidos()).isEqualTo(2);
    }
}
