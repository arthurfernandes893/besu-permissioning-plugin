# Besu Permissioning Plugin

O Plugin de Permissionamento é responsável por permissionar (permitir ou não) as conexões entre nós na rede através de uma chamada a um *smart contract*, garantindo **segurança**, **auditabilidade** e **eficiência**.

## Modo de uso
Para utilizar o plugin, é necessário:
- Possuir um nó configurado
- Na pasta acima daquela que contém o executável do Besu, criar (se ainda não houver sido criada) a pasta `plugins`
- Adicionar o arquivo `.jar` no diretório `plugins`
- Iniciar o nó Besu

## Funcionamento
O Plugin será carregado pelo Besu e acessará os serviços dos quais necessita, para que possa registrar um provedor de permissionamento (`NodePermissioningProvider`) perante o Besu. É esse provedor que realiza a chamada ao *smart contract* de permissionamento (no caso da RBB deve ser o `NodeRulesProxy`) para verificar, a cada pedido de conexão, se essa é permitida ou não. Para atingir esse objetivo, o plugin busca operar semelhantemente à forma como o permissionamento nativo do Besu operava.

O endereço do *smart contract* com as regras de permissionamento é acessado, por padrão, através da variável de ambiente `BESU_PERMISSIONS_NODES_CONTRACT_ADDRESS`, entretanto, pode ser personalizado através da opção de linha de comando `--plugin-permissioning-node-ingress-address` durante a inicialização do Besu.

Além disso, para garantir auditabilidade, foram adicionados logs ao fim de cada operação, no nível `DEBUG`, utilizando a biblioteca Log4j,

## Build
Em conformidade com as especificações do Besu, o plugin deve ser um arquivo `.jar` auto-contido ("fat Jar"). Para isso, utilizamos o conhecido plugin do Gradle, **Shadow**. Para realizar o build, tendo o Gradle já sido configurado, basta utilizar o comando:

```bash
gradlew shadowJar
```

**Observação**: Opcionalmente, caso já se tenha o gradle instalado localmente, pode-se utilizá-lo diretamente, com o comando `gradle shadowJar`, para realização do build.

O arquivo binário do plugin será gerado em `build/libs/besu-permissioning-plugin-<versao>-all.jar`

## Links Úteis:
- [Plugins | Besu documentation](https://besu.hyperledger.org/private-networks/concepts/plugins)
- [Permissioning plugin | Besu documentation](https://besu.hyperledger.org/private-networks/concepts/permissioning/plugin)
- [Hyperledger Besu Plugins — Overview | by Dimitar Danailov | Medium](https://medium.com/@d_danailov/hyperledger-besu-plugins-overview-28a241811c0c)
- [Webinar: Learn How to Leverage Plugin APIs on Hyperledger Besu (YouTube)](https://www.youtube.com/watch?v=78sa2WuA1rg)
