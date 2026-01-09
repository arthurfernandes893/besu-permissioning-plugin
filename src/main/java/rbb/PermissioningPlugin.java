/*
Copyright 2024 Rede Blockchain Brasil
Copyright 2025 Arthur Fernandes
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

Copied and adapted from Besu documentation (https://besu.hyperledger.org/private-networks/concepts/permissioning/plugin)

SPDX-License-Identifier: Apache-2.0
*/

package rbb;

import com.google.auto.service.AutoService;

import picocli.CommandLine.Option;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.apache.tuweni.bytes.Bytes;

import java.util.Optional;

import org.hyperledger.besu.plugin.ServiceManager;
import org.hyperledger.besu.plugin.BesuPlugin;

import org.hyperledger.besu.plugin.services.PermissioningService;
import org.hyperledger.besu.plugin.services.PicoCLIOptions;
import org.hyperledger.besu.plugin.services.BlockchainService;
import org.hyperledger.besu.plugin.services.TransactionSimulationService;

import org.hyperledger.besu.plugin.data.EnodeURL;
import org.hyperledger.besu.plugin.data.TransactionSimulationResult;

import org.hyperledger.besu.datatypes.Hash;

//import org.hyperledger.besu.datatypes.Transaction;
import org.hyperledger.besu.ethereum.core.Transaction;

import org.hyperledger.besu.evm.tracing.OperationTracer;


@AutoService(BesuPlugin.class)
public class PermissioningPlugin implements BesuPlugin{
  
  private static final Logger LOG = LogManager.getLogger(PermissioningPlugin.class);
  private static final String PLUGIN_PREFIX = "permissioning";
    
  private PermissioningService permissioning_service;
  private BlockchainService blockchain_service;
  private TransactionSimulationService txSimulation_service;

  /**
   * Registers the plugin with Besu. This method is called by Besu when loading the plugin.
   *
   * @param context The service manager which provides access to Besu services.
   */
  @Override
  public void register(ServiceManager context) {

    context
        .getService(PicoCLIOptions.class)
        .ifPresentOrElse(this::createPicoCLIOptions, () -> LOG.error("Could not obtain PicoCLIOptionsService"));

    context
        .getService(PermissioningService.class)
        .ifPresentOrElse(PermService -> this.permissioning_service = PermService, () -> LOG.error("Could not obtain PermissioningService"));

    context
          .getService(BlockchainService.class)
          .ifPresentOrElse(blockchainService -> this.blockchain_service = blockchainService, () -> LOG.error("Could not obtain BlockchainService"));

    context
          .getService(TransactionSimulationService.class)
          .ifPresentOrElse(TxSimService -> this.txSimulation_service = TxSimService, () -> LOG.error("Could not obtain TransactionSimulationService"));;
    
    permissioning_service.registerNodePermissioningProvider((sourceEnode, destinationEnode) -> {
      return checkConnectionAllowed(sourceEnode, destinationEnode);
    });

    permissioning_service.registerTransactionPermissioningProvider((tx) -> {
      return checkTxAllowed(tx);
    });
    
  }

  /**
   * Starts the plugin. This method is called by Besu after the plugin has been registered.
   */
  @Override
  public void start() {
    LOG.info("Successfully started PermissioningPlugin");
  }

  /**
   * Stops the plugin. This method is called by Besu when shutting down.
   */
  @Override
  public void stop() {
    LOG.info("Successfully stopped PermissioningPlugin");
  }

  /*
   * CLI Options
   */

  // CLI names must be of the form "--plugin-<namespace>-...."
  @Option(names = "--plugin-permissioning-node-ingress-address", description = "CLI option to set the address for the contract that will perform the Node Connection permissioning decision", defaultValue = "${env:BESU_PLUGIN_PERMISSIONING_NODE_INGRESS_ADDRESS}")
  public String nodeIngressAddress;
  
   // CLI names must be of the form "--plugin-<namespace>-...."
   @Option(names = "--plugin-permissioning-account-ingress-address", description = "CLI option to set the address for the contract that will perform the Account Transaction permissioning decision", defaultValue = "${env:BESU_PLUGIN_PERMISSIONING_ACCOUNT_INGRESS_ADDRESS}")
   public String accountIngressAddress;

  private void createPicoCLIOptions(final PicoCLIOptions picoCLIOptions) {
    picoCLIOptions.addPicoCLIOptions(PLUGIN_PREFIX, this);
  }
  

  /*
   * Function to perform the call to the NodeIngress Contract
   */

  /**
   * Checks if a connection between two nodes is allowed by simulating a transaction against a smart
   * contract.
   *
   * @param sourceEnode The enode URL of the source node.
   * @param destinationEnode The enode URL of the destination node.
   * @return {@code true} if the connection is allowed, {@code false} otherwise.
   */
  public boolean checkConnectionAllowed(final EnodeURL sourceEnode, final EnodeURL destinationEnode){
    
    LOG.trace(
        "Node permissioning - Smart Contract : Checking Connection {}", 
        sourceEnode.getNodeId());

    //obtaining blockchain head hash
    Hash chainHeadHash = blockchain_service.getChainHeadHash();

    //Create Transaction
    Transaction tx = PermissioningPluginFunctions
      .generateTransactionForSimulation(sourceEnode,destinationEnode,nodeIngressAddress);
    
    //simulation
    Optional<TransactionSimulationResult> txSimulationResult = txSimulation_service
      .simulate(tx,Optional.empty(), chainHeadHash, OperationTracer.NO_TRACING, true);

    return NodeConnectionSimulationReturnEval(txSimulationResult, destinationEnode);
  }


  /**
   * Checks if a transaction is allowed by simulating a call to a permissioning smart contract.
   *
   * @param transaction The transaction to be checked.
   * @return {@code true} if the transaction is allowed, {@code false} otherwise.
   */
  public boolean checkTxAllowed(final org.hyperledger.besu.datatypes.Transaction transaction) {

    LOG.trace(
        "Account permissioning - Smart Contract : Checking transaction {}", transaction.getHash());

    // obtaining blockchain head hash
    Hash chainHeadHash = blockchain_service.getChainHeadHash();

    // Create a new transaction for the simulation against the permissioning contract
    Transaction simulationTx =
        PermissioningPluginFunctions.generateTransactionForSimulation(
            transaction, accountIngressAddress);

    // simulation
    Optional<TransactionSimulationResult> txSimulationResult =
        txSimulation_service.simulate(
            simulationTx, Optional.empty(), 
            chainHeadHash, OperationTracer.NO_TRACING, true);

    return AccountTransactionSimulationReturnEval(txSimulationResult, transaction);
  }

  
  /*---
   * Function to check the result of the transaction simulation
   */
  @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
  public boolean NodeConnectionSimulationReturnEval(Optional<TransactionSimulationResult> txSimulationResult, EnodeURL destinationEnode){

    if (txSimulationResult.isEmpty()) {
      LOG.debug("Permissioning Tx did not happen. No result present");
      return false;
    }
    
    var result = txSimulationResult.get();
    
    if (!result.isSuccessful()) {
        LOG.debug("Permissioning Tx Simulation failed - Connection FORBIDDEN");
    
        result.getRevertReason().ifPresent(reason -> 
            LOG.trace("Permissioning Tx was REVERTED")
        );
    
        result.getInvalidReason().ifPresent(reason -> 
            LOG.trace("Permissioning Tx was INVALID - Reason: {}", reason)
        );
    
        return false;
    }
    final String ALLOW = "0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff";
    Bytes output = result.result().getOutput();

    if (output == null || output.isEmpty()) {
        LOG.debug("Permissioning Tx Simulation returned empty or null output - Connection FORBIDDEN");
        return false;
    }

    boolean isAllowed = output.compareTo(Bytes.fromHexString(ALLOW)) == 0;

    LOG.debug("Permissioning Tx {} - Connection {} for {}. Output: {}",
        isAllowed ? "SUCCESSFUL" : "UNSUCCESSFUL",
        isAllowed ? "ALLOWED" : "FORBIDDEN",
        destinationEnode,
        output.toHexString()
    );

    return isAllowed;
  }
  

  
  private boolean AccountTransactionSimulationReturnEval(
      Optional<TransactionSimulationResult> result,
      final org.hyperledger.besu.datatypes.Transaction transaction) {
    final String txHash = transaction.getHash().toString();
    final String sender = transaction.getSender().toString();
    final String recipient = transaction.getTo().map(Object::toString).orElse("contract-creation");

    if (result.isEmpty()) {
      LOG.warn(
          "Transaction {} from {} to {}: simulation resulted in an empty value. Blocking transaction by default.",
          txHash,
          sender,
          recipient);
      return false;
    }

    TransactionSimulationResult simulationResult = result.get();
    if (simulationResult.isSuccessful()) {
      Bytes output = simulationResult.result().getOutput();
      // The smart contract is expected to return a boolean.
      // In many cases, this is a 32-byte value padded with zeros, with the last byte being 0x01
      // for true or 0x00 for false.
      // This check is for a single byte or the last byte of a larger return value.
      if (!output.isEmpty() && output.get(output.size() - 1) == 1) {
        LOG.debug(
            "Transaction {} from {} to {}: permissioning simulation SUCCEEDED. Transaction ALLOWED.",
            txHash,
            sender,
            recipient);
        return true;
      } else {
        LOG.debug(
            "Transaction {} from {} to {}: permissioning simulation SUCCEEDED but returned false. Transaction FORBIDDEN.",
            txHash,
            sender,
            recipient);
        return false;
      }
    } else {
      LOG.warn(
          "Transaction {} from {} to {}: permissioning simulation FAILED. Transaction FORBIDDEN. Reason: {}",
          txHash,
          sender,
          recipient,
          simulationResult.getInvalidReason().orElse("Unknown simulation error."));
      return false;
    }
  }
}
