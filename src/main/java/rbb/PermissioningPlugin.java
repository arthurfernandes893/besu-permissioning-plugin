/*
Copyright 2024 Rede Blockchain Brasil

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

import org.hyperledger.besu.plugin.BesuContext;
import org.hyperledger.besu.plugin.BesuPlugin;

import org.hyperledger.besu.plugin.services.PermissioningService;
import org.hyperledger.besu.plugin.services.PicoCLIOptions;
import org.hyperledger.besu.plugin.services.BlockchainService;
import org.hyperledger.besu.plugin.services.TransactionSimulationService;

import org.hyperledger.besu.plugin.data.EnodeURL;
import org.hyperledger.besu.plugin.data.TransactionSimulationResult;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;

import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.permissioning.NodeSmartContractPermissioningController;
import org.hyperledger.besu.ethereum.transaction.CallParameter;

import org.hyperledger.besu.evm.tracing.OperationTracer;


@AutoService(BesuPlugin.class)
public class PermissioningPlugin implements BesuPlugin{
  
  private static Logger LOG = LogManager.getLogger(PermissioningPlugin.class);
  private static String PLUGIN_PREFIX = "permissioning";

  PermissioningService permissioning_service;
  BlockchainService blockchain_service;
  TransactionSimulationService txSimulation_service;

  @Override
  public void register(BesuContext context) {

    context
          .getService(PicoCLIOptions.class)
          .ifPresentOrElse(this::createPicoCLIOptions, () -> LOG.error("Could not obtain PicoCLIOptionsService"));

    permissioning_service = context.getService(PermissioningService.class).get(); 
    blockchain_service = context.getService(BlockchainService.class).get();
    txSimulation_service = context.getService(TransactionSimulationService.class).get();
    
    permissioning_service.registerNodePermissioningProvider((sourceEnode, destinationEnode) -> {
      return checkConnectionAllowed(sourceEnode, destinationEnode);
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
  @Option(names = "--plugin-permissioning-node-ingress-address", description = "CLI option to set the address for the contract that will perform the permissioning decision", defaultValue = "${env:BESU_PLUGIN_PERMISSIONING_NODE_INGRESS_ADDRESS}")
  public String nodeIngressAdress;

  private void createPicoCLIOptions(final PicoCLIOptions picoCLIOptions) {
    picoCLIOptions.addPicoCLIOptions(PLUGIN_PREFIX, this);
  }
  

/*
 * Function to perform the call to the NodeIngress Contract
 */

  public boolean checkConnectionAllowed(final EnodeURL sourceEnode, final EnodeURL destinationEnode){

    //obtaining blockchain head hash
    Hash chainHeadHash = blockchain_service.getChainHeadHash();

    //create Payload:
    Bytes functionSignature = PermissioningPluginFunctions.hashFunctionSignature();
    final Bytes txPayload = NodeSmartContractPermissioningController.createPayload(functionSignature, sourceEnode, destinationEnode);  

    //Create callParameters(from,to,gasLimit,gasPrice,value,payload)
    CallParameter callParams = new CallParameter(
                                                  null, 
                                                  Address.fromHexString(nodeIngressAdress),
                                                  -1, 
                                                  null, 
                                                  null, 
                                                  txPayload);

    //Create Transaction
    Transaction tx = PermissioningPluginFunctions.createTransactionForSimulation(callParams, -1);
    
    //simulation
    Optional<TransactionSimulationResult>  txSimulationResult = txSimulation_service.simulate(tx, chainHeadHash, OperationTracer.NO_TRACING, true);

    //positive return from Connection Allowed
    String allow = "0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff";

    if(txSimulationResult.isPresent()){
      if (txSimulationResult.get().isSuccessful()) {
        if( txSimulationResult.get().result().getOutput().compareTo(Bytes.fromHexString(allow)) == 0) {
          LOG.debug("Permissioning Tx SUCCESSFULL - Connection ALLOWED for {}",destinationEnode);
          return true;
        }   
        else{
          LOG.debug("Permissioning Tx UNSUCCESSFULL - Connection FORBIDDEN for {}",destinationEnode);
          return false;
        }  
      }
      else{ 
        LOG.debug("Permissioning Tx Simulation Happend but UNSUCCESSFULL - If smart contract address is correct, either REVERTED or INVALID - Connection FORBIDDEN", allow);

        if(!txSimulationResult.get().getRevertReason().isEmpty()){
          LOG.trace("Permissioning Tx Simulation Happend but UNSUCCESSFULL - Permissioning Tx was REVERTED");
        } 

        if(!txSimulationResult.get().getInvalidReason().isEmpty()){
          LOG.trace("Permissioning Tx Simulation Happend but UNSUCCESSFULL - Permissioning Tx was INVALID due to: {}", txSimulationResult.get().getInvalidReason().get());
        }
        
        return false;
      }
    }
    else{
      LOG.debug("Permissioning Tx did not happen. There is no result present");
      return false;
    }
  }


}
