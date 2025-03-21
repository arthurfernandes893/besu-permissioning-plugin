/*
Copyright Consensys Software Inc.
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

Copied and adapted from linea-sequencer (https://github.com/consensys/linea-sequencer)

SPDX-License-Identifier: Apache-2.0
*/

package rbb;

import java.math.BigInteger;

import org.apache.tuweni.bytes.Bytes;

import org.bouncycastle.asn1.sec.SECNamedCurves;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.params.ECDomainParameters;

import org.hyperledger.besu.crypto.SECPSignature;
import org.hyperledger.besu.crypto.Hash;

import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.datatypes.Address;

import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.permissioning.NodeSmartContractPermissioningController;
import org.hyperledger.besu.ethereum.permissioning.TransactionSmartContractPermissioningController;
import org.hyperledger.besu.ethereum.transaction.CallParameter;
import org.hyperledger.besu.plugin.data.EnodeURL;

import static java.nio.charset.StandardCharsets.UTF_8;

public class PermissioningPluginFunctions {
    
    public static final String NODE_FUNCTION_SIGNATURE = "connectionAllowed(bytes32,bytes32,bytes16,uint16,bytes32,bytes32,bytes16,uint16)";
    public static final String TX_FUNCTION_SIGNATURE = "transactionAllowed(address,address,uint256,uint256,uint256,bytes)";
    
    public static final Bytes NODE_FUNCTION_SIGNATURE_HASH = hashSignature(NODE_FUNCTION_SIGNATURE);
    public static final Bytes TX_FUNCTION_SIGNATURE_HASH = hashSignature(TX_FUNCTION_SIGNATURE);
    
    static {  
        //fake signature for transaction simulation
        final X9ECParameters params = SECNamedCurves.getByName("secp256k1");
        final ECDomainParameters curve = new ECDomainParameters(params.getCurve(), params.getG(), params.getN(), params.getH());
        FAKE_SIGNATURE_FOR_SIZE_CALCULATION =
          SECPSignature.create(
              new BigInteger("66397251408932042429874251838229702988618145381408295790259650671563847073199"),
              new BigInteger("24729624138373455972486746091821238755870276413282629437244319694880507882088"),
              (byte) 0,
              curve.getN());
    }

    public static final SECPSignature FAKE_SIGNATURE_FOR_SIZE_CALCULATION;

    private static Bytes hashSignature(final String signature) {
        return Hash.keccak256(Bytes.of(signature.getBytes(UTF_8))).slice(0, 4);
    }

    public static Transaction generateTransactionForSimulation(final EnodeURL sourceEnode, final EnodeURL destinationEnode, String nodeIngressAdress){
        //create Payload:
        final Bytes txPayload = NodeSmartContractPermissioningController
                                .createPayload(NODE_FUNCTION_SIGNATURE_HASH, sourceEnode, destinationEnode);  

        //Create Transaction
        return createTransactionForSimulation(-1, txPayload, nodeIngressAdress);
    }

    public static Transaction generateTransactionForSimulation(Transaction transaction, String accountIngressAddress){
        //create Payload:
        final Bytes txPayload = TransactionSmartContractPermissioningController
                                .createPayload(TX_FUNCTION_SIGNATURE_HASH, transaction);
        //Create Transaction
        return createTransactionForSimulation(-1, txPayload, accountIngressAddress); 
    }

    //inspired by: https://github.com/Consensys/linea-sequencer/blob/18458ee15a44c143a84f59f77d4247ed3893e12b/sequencer/src/main/java/net/consensys/linea/rpc/methods/LineaEstimateGas.java#L466
    private static Transaction createTransactionForSimulation(final long maxTxGasLimit, Bytes payload, String contractAddress) {
        
        CallParameter callParameters = new CallParameter(
                                                    null, 
                                                    Address.fromHexString(contractAddress),
                                                    -1, 
                                                    null, 
                                                    null, 
                                                    payload);

        return Transaction.builder()
                .sender(callParameters.getFrom())
                .to(callParameters.getTo())
                .gasLimit(maxTxGasLimit)
                .payload(
                    callParameters.getPayload() == null ? Bytes.EMPTY : callParameters.getPayload())
                .value(callParameters.getValue() == null ? Wei.ZERO : callParameters.getValue())
                .signature(FAKE_SIGNATURE_FOR_SIZE_CALCULATION)
                .gasPrice(Wei.ZERO)
                .build();
        

    }

}
