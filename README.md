# Besu Node and Transaction Permissioning Plugin

This is a forked repository from the original 'Besu Permissioning Plugin' first developed by the Rede Blockchain Brasil Team. The original code can be found in [the RBBNet plugin repo](https://github.com/RBBNet/besu-permissioning-plugin).

To include Transaction Permissioning as a functionality of this plugin, it was necessary to understand how Besu performs this task. To do so, the source code was searched so as to track the Classes and their hierarchy in this proccess. The [Besu Runner Builder](https://github.com/hyperledger/besu/blob/9f76c9cd9538a5aa072a0e3c1ffa51556cf09c1d/besu/src/main/java/org/hyperledger/besu/RunnerBuilder.java#L1133), [Besu Account Permissioning Controler](https://github.com/hyperledger/besu/blob/main/ethereum/permissioning/src/main/java/org/hyperledger/besu/ethereum/permissioning/account/AccountPermissioningController.java#L31) and the [Besu Transaction SmartContract Permissioning Controller](https://github.com/hyperledger/besu/blob/main/ethereum/permissioning/src/main/java/org/hyperledger/besu/ethereum/permissioning/TransactionSmartContractPermissioningController.java#L43) were the main source of inspiration/information for this plugin development.

Following the aim of Rede Blockchain Brasil to have its permissioning rely upon SmartContracts (on-chain permissioning), this plugin just "consults" the respective permisioning smartcontracts already deployed on the Blockchain through Transaction Simulation. For that, the plugin provides two environment variables for the user to inform the contracts' address : 

- `--plugin-permissioning-node-ingress-address` 

- `--plugin-permissioning-account-ingress-address`

## Requirements

## Usage

## Important Links

[Besu Runner Builder](https://github.com/hyperledger/besu/blob/9f76c9cd9538a5aa072a0e3c1ffa51556cf09c1d/besu/src/main/java/org/hyperledger/besu/RunnerBuilder.java#L1133)
[Besu Account Permissioning Controler](https://github.com/hyperledger/besu/blob/main/ethereum/permissioning/src/main/java/org/hyperledger/besu/ethereum/permissioning/account/AccountPermissioningController.java#L31)
[Besu Transaction SmartContract Permissioning Controller](https://github.com/hyperledger/besu/blob/main/ethereum/permissioning/src/main/java/org/hyperledger/besu/ethereum/permissioning/TransactionSmartContractPermissioningController.java#L43)