package br.pucgoias.ads.delivery.excecao;

public class ItemDuplicadoException extends RuntimeException {

    public ItemDuplicadoException(String codigo) {
        super("Esse código ja existente no cardapio: " + codigo);
    }
}
