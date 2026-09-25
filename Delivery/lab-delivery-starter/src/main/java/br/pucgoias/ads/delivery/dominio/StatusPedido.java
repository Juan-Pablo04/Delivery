package br.pucgoias.ads.delivery.dominio;

import br.pucgoias.ads.delivery.excecao.TransicaoInvalidaException;

public enum StatusPedido {
    RECEBIDO,
    EM_PREPARO,
    SAIU_PARA_ENTREGA,
    ENTREGUE,
    CANCELADO;

    public StatusPedido proximo() {
        return switch (this) {
            case RECEBIDO -> EM_PREPARO;
            case EM_PREPARO -> SAIU_PARA_ENTREGA;
            case SAIU_PARA_ENTREGA -> ENTREGUE;
            case ENTREGUE -> throw new TransicaoInvalidaException(this, "avancarStatus");
            case CANCELADO -> throw new TransicaoInvalidaException(this, "avancarStatus");
        };
    }

    public boolean permiteCancelamento() {
        return this == RECEBIDO;
    }
}
