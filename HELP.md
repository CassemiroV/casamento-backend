# Getting Started

### Reference Documentation
For further reference, please consider the following sections:

* [Official Apache Maven documentation](https://maven.apache.org/guides/index.html)
* [Spring Boot Maven Plugin Reference Guide](https://docs.spring.io/spring-boot/4.1.1/maven-plugin)
* [Create an OCI image](https://docs.spring.io/spring-boot/4.1.1/maven-plugin/build-image.html)
* [Spring Web](https://docs.spring.io/spring-boot/4.1.1/reference/web/servlet.html)
* [Spring Boot DevTools](https://docs.spring.io/spring-boot/4.1.1/reference/using/devtools.html)
* [Spring Data JPA](https://docs.spring.io/spring-boot/4.1.1/reference/data/sql.html#data.sql.jpa-and-spring-data)
* [Validation](https://docs.spring.io/spring-boot/4.1.1/reference/io/validation.html)

### Guides
The following guides illustrate how to use some features concretely:

* [Building a RESTful Web Service](https://spring.io/guides/gs/rest-service/)
* [Serving Web Content with Spring MVC](https://spring.io/guides/gs/serving-web-content/)
* [Building REST services with Spring](https://spring.io/guides/tutorials/rest/)
* [Accessing Data with JPA](https://spring.io/guides/gs/accessing-data-jpa/)
* [Validation](https://spring.io/guides/gs/validating-form-input/)

### Maven Parent overrides

Due to Maven's design, elements are inherited from the parent POM to the project POM.
While most of the inheritance is fine, it also inherits unwanted elements like `<license>` and `<developers>` from the parent.
To prevent this, the project POM contains empty overrides for these elements.
If you manually switch to a different parent and actually want the inheritance, you need to remove those overrides.


## Pagamentos via Mercado Pago

1. No ambiente de execução do backend, configure `MERCADO_PAGO_ACCESS_TOKEN` e
   `MERCADO_PAGO_WEBHOOK_SECRET` com os valores da aplicação do Mercado Pago.
2. No painel do Mercado Pago, abra **Configuração de Webhooks**, selecione
   **Order (Mercado Pago)** e informe `https://<host-da-api>/payments/webhooks/mercadopago`.
   Configure no ambiente correspondente às credenciais. Para simular a entrega,
   use o ID de uma ordem existente nesse ambiente.
   [Documentação do webhook](https://www.mercadopago.com.br/developers/pt/docs/checkout-pro-orders/notifications?scope=prod)
3. Para retorno automático ao site, configure `MERCADO_PAGO_CHECKOUT_RETURN_URL`
   com a URL HTTPS da página que exibe o status no frontend. O Mercado Pago
   inclui a referência local em `external_reference` quando disponível.
   [Documentação das URLs de retorno](https://www.mercadopago.com.br/developers/pt/docs/checkout-pro-orders/web-integration/configure-back-urls?scope=prod)

O frontend recebe `paymentId` e `checkoutUrl` de `POST /payments/pay`. Guarde
o ID antes de redirecionar. Ao retornar do checkout, consulte
`GET /payments/{paymentId}` usando o ID guardado ou o `external_reference`.
A resposta contém `status` (`PENDING`, `PAID`, `FAILED` ou `CANCELLED`),
além de `gatewayStatus` e `gatewayStatusDetail`. Enquanto houver pendência,
consulte novamente a cada 10 segundos. A consulta também concilia pagamentos
antigos em aberto quando a ordem já está vinculada ao registro local.

Uma ordem aberta impede a abertura de outro checkout para o mesmo presente.
As novas ordens têm validade de um dia. Um reembolso integral aparece como
`CANCELLED` com `gatewayStatus=refunded`. O presente é liberado quando
nenhum outro pagamento para ele permanece aprovado.
