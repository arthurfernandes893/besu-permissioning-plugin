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

  @Override
  public void start() {
    LOG.info("Successfully started PermissioningPlugin");
  }

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

  public boolean checkConnectionAllowed(final EnodeURL sourceEnode, final EnodeURL destinationEnode){
    
    LOG.trace(
        "Node permissioning - Smart Contract : Checking Connection {}", 
        sourceEnode.getNodeId());

    //obtaining blockchain head hash
    Hash chainHeadHash = blockchain_service.getChainHeadHash();

    //Create Transaction
    Transaction tx = PermissioningPluginFunctions.generateTransactionForSimulation(sourceEnode,destinationEnode,nodeIngressAddress);
    
    //simulation
    Optional<TransactionSimulationResult> txSimulationResult = txSimulation_service.simulate(tx,Optional.empty(), chainHeadHash, OperationTracer.NO_TRACING, true);

    return NodeConnectionSimulationReturnEval(txSimulationResult, destinationEnode);
  }


  /*
   * Function to perform the call to the AccountIngress Contract
   */
  public boolean checkTxAllowed(final org.hyperledger.besu.datatypes.Transaction transaction){
    
    LOG.trace(
      "Account permissioning - Smart Contract : Checking Transaction to address {}",
      transaction.getTo().get());
      
    //obtaining blockchain head hash
    Hash chainHeadHash = blockchain_service.getChainHeadHash();

    //simulation
    Optional<TransactionSimulationResult> txSimulationResult = txSimulation_service.simulate(transaction,Optional.empty(), chainHeadHash, OperationTracer.NO_TRACING, true);

    return AccountTransactionSimulationReturnEval(txSimulationResult);

  }

  
  /*---
   * Function to check the result of the transaction simulation
   */
  @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
  private boolean NodeConnectionSimulationReturnEval(Optional<TransactionSimulationResult> txSimulationResult, EnodeURL destinationEnode){

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
    boolean isAllowed = result.result().getOutput().compareTo(Bytes.fromHexString(ALLOW)) == 0;
    
    LOG.debug("Permissioning Tx {} - Connection {} for {}",
        isAllowed ? "SUCCESSFUL" : "UNSUCCESSFUL",
        isAllowed ? "ALLOWED" : "FORBIDDEN",
        destinationEnode
    );
    
    return isAllowed;
  }
  

  
  private boolean AccountTransactionSimulationReturnEval(Optional<TransactionSimulationResult> result) {
    if (result.isEmpty()) {
      LOG.warn("Account transaction simulation resulted in an empty value. Blocking transaction by default.");
      return false;
    }

    TransactionSimulationResult simulationResult = result.get();
    if (simulationResult.isSuccessful()) {
      Bytes output = simulationResult.result().getOutput();
      if (!output.isEmpty() && output.get(output.size() - 1) == 1) {
        LOG.debug("Transaction simulation returned success with allowed state. Transaction permitted.");
        return true;
      } else {
        LOG.debug("Transaction simulation returned success with disallowed state. Blocking transaction.");
        return false;
      }
    } else {
      LOG.warn("Transaction simulation failed. Blocking transaction. Reason: {}", simulationResult.getInvalidReason().orElse("Unknown simulation error."));
      return false;
    }
  }
}
