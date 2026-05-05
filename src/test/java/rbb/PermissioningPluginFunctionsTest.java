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
*/

package rbb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.permissioning.NodeSmartContractPermissioningController;
import org.hyperledger.besu.ethereum.permissioning.TransactionSmartContractPermissioningController;
import org.hyperledger.besu.plugin.data.EnodeURL;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

class PermissioningPluginFunctionsTest {

  @Test
  void generateTransactionForSimulationBuildsNodeCall() {
    EnodeURL source = mock(EnodeURL.class);
    EnodeURL destination = mock(EnodeURL.class);
    Bytes payload = Bytes.fromHexString("0x1234");
    String contractAddress = "0x0000000000000000000000000000000000000001";

    try (MockedStatic<NodeSmartContractPermissioningController> mocked =
        mockStatic(NodeSmartContractPermissioningController.class)) {
      mocked
          .when(() ->
              NodeSmartContractPermissioningController.createPayload(
                  eq(PermissioningPluginFunctions.NODE_FUNCTION_SIGNATURE_HASH),
                  eq(source),
                  eq(destination)))
          .thenReturn(payload);

      Transaction tx =
          PermissioningPluginFunctions.generateTransactionForSimulation(
              source, destination, contractAddress);

      mocked.verify(() ->
          NodeSmartContractPermissioningController.createPayload(
              eq(PermissioningPluginFunctions.NODE_FUNCTION_SIGNATURE_HASH),
              eq(source),
              eq(destination)));
      assertThat(tx.getTo()).isEqualTo(Address.fromHexString(contractAddress));
      assertThat(tx.getPayload()).isEqualTo(payload);
      assertThat(tx.getGasPrice()).isEqualTo(Wei.ZERO);
      assertThat(tx.getValue()).isEqualTo(Wei.ZERO);
      assertThat(tx.getSignature())
          .isEqualTo(PermissioningPluginFunctions.FAKE_SIGNATURE_FOR_SIZE_CALCULATION);
      assertThat(tx.getGasLimit()).isEqualTo(-1);
    }
  }

  @Test
  void generateTransactionForSimulationBuildsAccountCall() {
    org.hyperledger.besu.datatypes.Transaction baseTx =
        mock(org.hyperledger.besu.datatypes.Transaction.class);
    Address sender = Address.fromHexString("0x0000000000000000000000000000000000000002");
    Address to = Address.fromHexString("0x0000000000000000000000000000000000000003");
    Bytes payload = Bytes.fromHexString("0x4567");
    when(baseTx.getSender()).thenReturn(sender);
    when(baseTx.getTo()).thenReturn(Optional.of(to));
    when(baseTx.getGasLimit()).thenReturn(21_000L);
    when(baseTx.getPayload()).thenReturn(payload);

    Bytes simulationPayload = Bytes.fromHexString("0xabcd");
    String contractAddress = "0x0000000000000000000000000000000000000004";
    ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);

    try (MockedStatic<TransactionSmartContractPermissioningController> mocked =
        mockStatic(TransactionSmartContractPermissioningController.class)) {
      mocked
          .when(() ->
              TransactionSmartContractPermissioningController.createPayload(
                  eq(PermissioningPluginFunctions.TX_FUNCTION_SIGNATURE_HASH),
                  any(Transaction.class)))
          .thenReturn(simulationPayload);

      Transaction tx =
          PermissioningPluginFunctions.generateTransactionForSimulation(
              baseTx, contractAddress);

      mocked.verify(() ->
          TransactionSmartContractPermissioningController.createPayload(
              eq(PermissioningPluginFunctions.TX_FUNCTION_SIGNATURE_HASH),
              txCaptor.capture()));

      Transaction captured = txCaptor.getValue();
      assertThat(captured.getSender()).isEqualTo(sender);
      assertThat(captured.getTo()).contains(to);
      assertThat(captured.getGasLimit()).isEqualTo(21_000L);
      assertThat(captured.getPayload()).isEqualTo(payload);
      assertThat(captured.getGasPrice()).isEqualTo(Wei.ZERO);
      assertThat(captured.getSignature())
          .isEqualTo(PermissioningPluginFunctions.FAKE_SIGNATURE_FOR_SIZE_CALCULATION);

      assertThat(tx.getTo()).isEqualTo(Address.fromHexString(contractAddress));
      assertThat(tx.getPayload()).isEqualTo(simulationPayload);
      assertThat(tx.getGasPrice()).isEqualTo(Wei.ZERO);
      assertThat(tx.getValue()).isEqualTo(Wei.ZERO);
    }
  }
}
