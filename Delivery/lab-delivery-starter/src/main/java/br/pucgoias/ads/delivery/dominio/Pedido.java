package br.pucgoias.ads.delivery.dominio;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import br.pucgoias.ads.delivery.excecao.TransicaoInvalidaException;

@Document(collection = "pedidos")
public class Pedido {

    @Id
    private String id;

    @Indexed
    private String restauranteId;

    private Cliente cliente;

    private List<ItemPedido> itens;

    private StatusPedido status;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal total;

    private Instant criadoEm;

    @Version
    private Long versao;

    protected Pedido() {
    }

    public Pedido(String restauranteId, Cliente cliente, List<ItemPedido> itens) {
        this.restauranteId = restauranteId;
        this.cliente = cliente;
        this.itens = List.copyOf(itens);
        this.criadoEm = Instant.now();
        this.status = StatusPedido.RECEBIDO;
        this.total = BigDecimal.ZERO;
        for (ItemPedido item : this.itens) {
            this.total = this.total.add(item.subtotal());
        }
    }

    public void avancarStatus() {
        this.status = this.status.proximo();
    }

    public void cancelar() {
        if (!this.status.permiteCancelamento()) {
            throw new TransicaoInvalidaException(this.status, "cancelar");
        }
        this.status = StatusPedido.CANCELADO;
    }

    public String getId() { return id; }
    public String getRestauranteId() { return restauranteId; }
    public Cliente getCliente() { return cliente; }
    public List<ItemPedido> getItens() { return itens; }
    public StatusPedido getStatus() { return status; }
    public BigDecimal getTotal() { return total; }
    public Instant getCriadoEm() { return criadoEm; }
    public Long getVersao() { return versao; }
}
