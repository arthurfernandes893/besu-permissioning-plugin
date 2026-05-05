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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.evm.tracing.OperationTracer;
import org.hyperledger.besu.plugin.BesuPlugin;
import org.hyperledger.besu.plugin.ServiceManager;
import org.hyperledger.besu.plugin.data.EnodeURL;
import org.hyperledger.besu.plugin.data.TransactionSimulationResult;
import org.hyperledger.besu.plugin.services.BlockchainService;
import org.hyperledger.besu.plugin.services.PermissioningService;
import org.hyperledger.besu.plugin.services.PicoCLIOptions;
import org.hyperledger.besu.plugin.services.TransactionSimulationService;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class PermissioningPluginTest {

  private static final String NODE_ALLOW_OUTPUT =
      "0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff";

  @Test
  void registerWithAllServicesRegistersProvidersAndOptions() {
    PermissioningPlugin plugin = new PermissioningPlugin();
    ServiceManager serviceManager = mock(ServiceManager.class);
    PicoCLIOptions picoCLIOptions = mock(PicoCLIOptions.class);
    PermissioningService permissioningService = mock(PermissioningService.class);
    BlockchainService blockchainService = mock(BlockchainService.class);
    TransactionSimulationService simulationService = mock(TransactionSimulationService.class);

    when(serviceManager.getService(PicoCLIOptions.class)).thenReturn(Optional.of(picoCLIOptions));
    when(serviceManager.getService(PermissioningService.class))
        .thenReturn(Optional.of(permissioningService));
    when(serviceManager.getService(BlockchainService.class))
        .thenReturn(Optional.of(blockchainService));
    when(serviceManager.getService(TransactionSimulationService.class))
        .thenReturn(Optional.of(simulationService));

    plugin.register(serviceManager);

    verify(picoCLIOptions).addPicoCLIOptions("permissioning", plugin);
    verify(permissioningService).registerNodePermissioningProvider(any());
    verify(permissioningService).registerTransactionPermissioningProvider(any());
  }

  @Test
  void checkConnectionAllowedReturnsTrueWhenSimulationAllows() throws Exception {
    PermissioningPlugin plugin = new PermissioningPlugin();
    plugin.nodeIngressAddress = "0x0000000000000000000000000000000000000001";
    BlockchainService blockchainService = mock(BlockchainService.class);
    TransactionSimulationService simulationService = mock(TransactionSimulationService.class);
    setField(plugin, "blockchain_service", blockchainService);
    setField(plugin, "txSimulation_service", simulationService);

    Hash chainHeadHash = Hash.fromHexString("0x" + "11".repeat(32));
    when(blockchainService.getChainHeadHash()).thenReturn(chainHeadHash);

    Transaction dummyTx =
        Transaction.builder()
            .sender(Address.fromHexString("0x0000000000000000000000000000000000000002"))
            .to(Address.fromHexString("0x0000000000000000000000000000000000000003"))
            .gasLimit(21_000)
            .payload(Bytes.EMPTY)
            .signature(PermissioningPluginFunctions.FAKE_SIGNATURE_FOR_SIZE_CALCULATION)
            .gasPrice(Wei.ZERO)
            .build();

    TransactionSimulationResult simulationResult = mock(TransactionSimulationResult.class);
    TransactionSimulationResult.Result result = mock(TransactionSimulationResult.Result.class);
    when(simulationResult.isSuccessful()).thenReturn(true);
    when(simulationResult.result()).thenReturn(result);
    when(result.getOutput()).thenReturn(Bytes.fromHexString(NODE_ALLOW_OUTPUT));

    when(simulationService.simulate(
            eq(dummyTx), any(), eq(chainHeadHash), eq(OperationTracer.NO_TRACING), eq(true)))
        .thenReturn(Optional.of(simulationResult));

    EnodeURL source = mock(EnodeURL.class);
    EnodeURL destination = mock(EnodeURL.class);

    try (MockedStatic<PermissioningPluginFunctions> mocked =
        mockStatic(PermissioningPluginFunctions.class)) {
      mocked
          .when(() ->
              PermissioningPluginFunctions.generateTransactionForSimulation(
                  eq(source), eq(destination), eq(plugin.nodeIngressAddress)))
          .thenReturn(dummyTx);

      boolean allowed = plugin.checkConnectionAllowed(source, destination);

      assertThat(allowed).isTrue();
      mocked.verify(() ->
          PermissioningPluginFunctions.generateTransactionForSimulation(
              eq(source), eq(destination), eq(plugin.nodeIngressAddress)));
    }
  }

  @Test
  void checkConnectionAllowedReturnsFalseWhenSimulationEmpty() throws Exception {
    PermissioningPlugin plugin = new PermissioningPlugin();
    plugin.nodeIngressAddress = "0x0000000000000000000000000000000000000001";
    BlockchainService blockchainService = mock(BlockchainService.class);
    TransactionSimulationService simulationService = mock(TransactionSimulationService.class);
    setField(plugin, "blockchain_service", blockchainService);
    setField(plugin, "txSimulation_service", simulationService);

    Hash chainHeadHash = Hash.fromHexString("0x" + "22".repeat(32));
    when(blockchainService.getChainHeadHash()).thenReturn(chainHeadHash);

    Transaction dummyTx =
        Transaction.builder()
            .sender(Address.fromHexString("0x0000000000000000000000000000000000000002"))
            .to(Address.fromHexString("0x0000000000000000000000000000000000000003"))
            .gasLimit(21_000)
            .payload(Bytes.EMPTY)
            .signature(PermissioningPluginFunctions.FAKE_SIGNATURE_FOR_SIZE_CALCULATION)
            .gasPrice(Wei.ZERO)
            .build();

    when(simulationService.simulate(
            eq(dummyTx), any(), eq(chainHeadHash), eq(OperationTracer.NO_TRACING), eq(true)))
        .thenReturn(Optional.empty());

    EnodeURL source = mock(EnodeURL.class);
    EnodeURL destination = mock(EnodeURL.class);

    try (MockedStatic<PermissioningPluginFunctions> mocked =
        mockStatic(PermissioningPluginFunctions.class)) {
      mocked
          .when(() ->
              PermissioningPluginFunctions.generateTransactionForSimulation(
                  eq(source), eq(destination), eq(plugin.nodeIngressAddress)))
          .thenReturn(dummyTx);

      boolean allowed = plugin.checkConnectionAllowed(source, destination);

      assertThat(allowed).isFalse();
    }
  }

  @Test
  void checkTxAllowedReturnsTrueWhenSimulationReturnsTrue() throws Exception {
    PermissioningPlugin plugin = new PermissioningPlugin();
    plugin.accountIngressAddress = "0x0000000000000000000000000000000000000004";
    BlockchainService blockchainService = mock(BlockchainService.class);
    TransactionSimulationService simulationService = mock(TransactionSimulationService.class);
    setField(plugin, "blockchain_service", blockchainService);
    setField(plugin, "txSimulation_service", simulationService);

    Hash chainHeadHash = Hash.fromHexString("0x" + "33".repeat(32));
    when(blockchainService.getChainHeadHash()).thenReturn(chainHeadHash);

    Transaction dummyTx =
        Transaction.builder()
            .sender(Address.fromHexString("0x0000000000000000000000000000000000000005"))
            .to(Address.fromHexString("0x0000000000000000000000000000000000000006"))
            .gasLimit(21_000)
            .payload(Bytes.EMPTY)
            .signature(PermissioningPluginFunctions.FAKE_SIGNATURE_FOR_SIZE_CALCULATION)
            .gasPrice(Wei.ZERO)
            .build();

    TransactionSimulationResult simulationResult = mock(TransactionSimulationResult.class);
    TransactionSimulationResult.Result result = mock(TransactionSimulationResult.Result.class);
    when(simulationResult.isSuccessful()).thenReturn(true);
    when(simulationResult.result()).thenReturn(result);
    when(result.getOutput()).thenReturn(Bytes.fromHexString("0x01"));

    when(simulationService.simulate(
            eq(dummyTx), any(), eq(chainHeadHash), eq(OperationTracer.NO_TRACING), eq(true)))
        .thenReturn(Optional.of(simulationResult));

    org.hyperledger.besu.datatypes.Transaction tx =
        mock(org.hyperledger.besu.datatypes.Transaction.class);
    when(tx.getHash()).thenReturn(Hash.fromHexString("0x" + "44".repeat(32)));
    when(tx.getSender()).thenReturn(Address.fromHexString("0x0000000000000000000000000000000000000007"));
    when(tx.getTo())
        .thenReturn(Optional.of(Address.fromHexString("0x0000000000000000000000000000000000000008")));

    try (MockedStatic<PermissioningPluginFunctions> mocked =
        mockStatic(PermissioningPluginFunctions.class)) {
      mocked
          .when(() ->
              PermissioningPluginFunctions.generateTransactionForSimulation(
                  eq(tx), eq(plugin.accountIngressAddress)))
          .thenReturn(dummyTx);

      boolean allowed = plugin.checkTxAllowed(tx);

      assertThat(allowed).isTrue();
      mocked.verify(() ->
          PermissioningPluginFunctions.generateTransactionForSimulation(
              eq(tx), eq(plugin.accountIngressAddress)));
    }
  }

  @Test
  void checkTxAllowedReturnsFalseWhenSimulationFails() throws Exception {
    PermissioningPlugin plugin = new PermissioningPlugin();
    plugin.accountIngressAddress = "0x0000000000000000000000000000000000000004";
    BlockchainService blockchainService = mock(BlockchainService.class);
    TransactionSimulationService simulationService = mock(TransactionSimulationService.class);
    setField(plugin, "blockchain_service", blockchainService);
    setField(plugin, "txSimulation_service", simulationService);

    Hash chainHeadHash = Hash.fromHexString("0x" + "55".repeat(32));
    when(blockchainService.getChainHeadHash()).thenReturn(chainHeadHash);

    Transaction dummyTx =
        Transaction.builder()
            .sender(Address.fromHexString("0x0000000000000000000000000000000000000005"))
            .to(Address.fromHexString("0x0000000000000000000000000000000000000006"))
            .gasLimit(21_000)
            .payload(Bytes.EMPTY)
            .signature(PermissioningPluginFunctions.FAKE_SIGNATURE_FOR_SIZE_CALCULATION)
            .gasPrice(Wei.ZERO)
            .build();

    TransactionSimulationResult simulationResult = mock(TransactionSimulationResult.class);
    when(simulationResult.isSuccessful()).thenReturn(false);
    when(simulationResult.getInvalidReason()).thenReturn(Optional.of("invalid"));

    when(simulationService.simulate(
            eq(dummyTx), any(), eq(chainHeadHash), eq(OperationTracer.NO_TRACING), eq(true)))
        .thenReturn(Optional.of(simulationResult));

    org.hyperledger.besu.datatypes.Transaction tx =
        mock(org.hyperledger.besu.datatypes.Transaction.class);
    when(tx.getHash()).thenReturn(Hash.fromHexString("0x" + "66".repeat(32)));
    when(tx.getSender()).thenReturn(Address.fromHexString("0x0000000000000000000000000000000000000007"));
    when(tx.getTo())
        .thenReturn(Optional.of(Address.fromHexString("0x0000000000000000000000000000000000000008")));

    try (MockedStatic<PermissioningPluginFunctions> mocked =
        mockStatic(PermissioningPluginFunctions.class)) {
      mocked
          .when(() ->
              PermissioningPluginFunctions.generateTransactionForSimulation(
                  eq(tx), eq(plugin.accountIngressAddress)))
          .thenReturn(dummyTx);

      boolean allowed = plugin.checkTxAllowed(tx);

      assertThat(allowed).isFalse();
    }
  }

  private static void setField(Object target, String fieldName, Object value) throws Exception {
    Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(target, value);
  }
}
