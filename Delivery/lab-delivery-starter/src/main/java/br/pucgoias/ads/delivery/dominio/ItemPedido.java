package br.pucgoias.ads.delivery.dominio;

import java.math.BigDecimal;

import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

public record ItemPedido(
        String codigo,
        String nome,
        @Field(targetType = FieldType.DECIMAL128) BigDecimal precoUnitario,
        int quantidade) {

    public BigDecimal subtotal() {
        return precoUnitario.multiply(BigDecimal.valueOf(quantidade));
    }
}
