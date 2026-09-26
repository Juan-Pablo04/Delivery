package br.pucgoias.ads.delivery.dominio;

import java.math.BigDecimal;

public record FaturamentoRestaurante(String restauranteId, BigDecimal faturamento, long quantidadePedidos) {
}
