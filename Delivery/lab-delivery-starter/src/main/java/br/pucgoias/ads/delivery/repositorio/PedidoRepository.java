package br.pucgoias.ads.delivery.repositorio;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import br.pucgoias.ads.delivery.dominio.Pedido;
import br.pucgoias.ads.delivery.dominio.StatusPedido;

public interface PedidoRepository extends MongoRepository<Pedido, String> {

    List<Pedido> findByRestauranteIdOrderByCriadoEmDesc(String restauranteId);

    List<Pedido> findByStatus(StatusPedido status);

    @Query("{ 'cliente.telefone': ?0 }")
    List<Pedido> buscarPorTelefoneDoCliente(String telefone);
}
