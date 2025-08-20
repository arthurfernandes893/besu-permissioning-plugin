# Besu Node and Transaction Permissioning Plugin

This repository contains a Besu plugin that performs node connection and transaction permissioning by consulting on-chain smart contracts via transaction simulation. It extends the original plugin by Rede Blockchain Brasil (RBB) to include transaction permissioning.

- Original [RBB plugin](https://github.com/RBBNet/besu-permissioning-plugin)
- Main plugin entry point: `rbb.PermissioningPlugin`
- Supporting helpers: `rbb.PermissioningPluginFunctions`

### Research and Inspiration
To include Transaction Permissioning as a functionality of this plugin, it was necessary to understand how Besu performs this task. To do so, the source code was searched so as to track the Classes and their hierarchy in this proccess. The [Besu Runner Builder](https://github.com/hyperledger/besu/blob/9f76c9cd9538a5aa072a0e3c1ffa51556cf09c1d/besu/src/main/java/org/hyperledger/besu/RunnerBuilder.java#L1133), [Besu Account Permissioning Controler](https://github.com/hyperledger/besu/blob/main/ethereum/permissioning/src/main/java/org/hyperledger/besu/ethereum/permissioning/account/AccountPermissioningController.java#L31) and the [Besu Transaction SmartContract Permissioning Controller](https://github.com/hyperledger/besu/blob/main/ethereum/permissioning/src/main/java/org/hyperledger/besu/ethereum/permissioning/TransactionSmartContractPermissioningController.java#L43) were the main source of inspiration/information for this plugin development.

Following the aim of Rede Blockchain Brasil to have its permissioning rely upon SmartContracts (on-chain permissioning), this plugin just "consults" the respective permisioning smartcontracts already deployed on the Blockchain through Transaction Simulation. For that, the plugin provides two environment variables for the user to inform the contracts' address : 

- `--plugin-permissioning-node-ingress-address` 

- `--plugin-permissioning-account-ingress-address`

## How it works

### What this plugin does

- Node connection permissioning: intercepts inbound/outbound peer connection attempts and checks if they are allowed by a smart contract (Node Ingress).
- Transaction permissioning: intercepts transactions and checks if they are allowed by a smart contract (Account/Transaction Ingress) using Besu’s transaction simulation.

The plugin integrates with Besu via:
- PermissioningService
- BlockchainService
- TransactionSimulationService
- PicoCLIOptions

And registers:
- NodeConnectionPermissioningProvider
- TransactionPermissioningProvider

### Plugin registration
At startup, `PermissioningPlugin.register` acquires Besu services and registers two providers:
- Node provider that delegates to `checkConnectionAllowed(source, destination)`
- Transaction provider that delegates to `checkTxAllowed(transaction)`

It also registers CLI options so you can configure the contract addresses.

### Node connection permissioning flow
1. Besu asks the plugin whether a connection to a peer is permitted.
2. The plugin builds a call to the Node Ingress contract function:
   - Function signature (conceptual): `connectionAllowed(bytes32,bytes32,bytes16,uint16,bytes32,bytes32,bytes16,uint16)`
   - ABI selector precomputed in `PermissioningPluginFunctions.NODE_FUNCTION_SIGNATURE_HASH`
3. The plugin uses node identity and endpoint data to build the call data and executes a read-only contract call (via transaction simulation) to get an allow/deny decision.
4. The boolean result is returned to Besu to allow or block the connection.

### Transaction permissioning flow
1. Besu asks the plugin whether a transaction is permitted.
2. The plugin builds a call to the Account/Transaction Ingress contract function:
   - Function signature (conceptual): `transactionAllowed(address,address,uint256,uint256,uint256,bytes)`
   - ABI selector precomputed in `PermissioningPluginFunctions.TX_FUNCTION_SIGNATURE_HASH`
3. The plugin encodes sender/recipient, value, gas fields, and calldata. For simulation sizing, a static fake signature is provided in `PermissioningPluginFunctions`.
4. Using `TransactionSimulationService` and `BlockchainService`, the plugin simulates the call against the current head without broadcasting a transaction.
5. The simulation result determines if the transaction is permitted; the boolean is returned to Besu.

This approach mirrors Besu’s native on-chain permissioning strategy while keeping the logic inside a plugin.

## Configuration

You can configure the smart contract addresses via CLI flags or environment variables (names may vary depending on your implementation). CLI options are defined in `PermissioningPlugin`:

- Node permissioning contract:
  - CLI: `--plugin-permissioning-node-ingress-address`
  - Environment: `BESU_PLUGIN_PERMISSIONING_NODE_INGRESS_ADDRESS`
- Transaction permissioning contract:
  - CLI: `--plugin-permissioning-account-ingress-address`
  - Environment: `BESU_PLUGIN_PERMISSIONING_ACCOUNT_INGRESS_ADDRESS`

Examples:
- `--plugin-permissioning-node-ingress-address=0xabc...`
- `--plugin-permissioning-account-ingress-address=0xdef...`

## Build

The plugin produces a self-contained “fat JAR” using Gradle Shadow.

- Build with wrapper:
  ```bash
  ./gradlew shadowJar
  ```
## Installation and usage
1. Ensure your Besu node is configured.
2. Create a plugins directory one level above the Besu executable if it doesn’t exist.
3. Copy the fat JAR to the plugins/ directory.
4. Start Besu adding the plugin CLI flags, for example:
    ```bash
    besu \
    --plugin-permissioning-node-ingress-address=0x0000000000000000000000000000000000000001 \
    --plugin-permissioning-account-ingress-address=0x0000000000000000000000000000000000000002
    ```
## Important Links

- [Besu Runner Builder](https://github.com/hyperledger/besu/blob/9f76c9cd9538a5aa072a0e3c1ffa51556cf09c1d/besu/src/main/java/org/hyperledger/besu/RunnerBuilder.java#L1133)
- [Besu Account Permissioning Controler](https://github.com/hyperledger/besu/blob/main/ethereum/permissioning/src/main/java/org/hyperledger/besu/ethereum/permissioning/account/AccountPermissioningController.java#L31)
- [Besu Transaction SmartContract Permissioning Controller](https://github.com/hyperledger/besu/blob/main/ethereum/permissioning/src/main/java/org/hyperledger/besu/ethereum/permissioning/TransactionSmartContractPermissioningController.java#L43)


