package rbb;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.hyperledger.besu.plugin.services.BlockchainService;
import org.hyperledger.besu.plugin.services.TransactionSimulationService;

import org.hyperledger.besu.plugin.data.EnodeURL;
import org.hyperledger.besu.plugin.data.TransactionSimulationResult;

import org.hyperledger.besu.datatypes.Hash;

//import org.hyperledger.besu.datatypes.Transaction;
import org.hyperledger.besu.ethereum.core.Transaction;

import org.hyperledger.besu.evm.tracing.OperationTracer;

@ExtendWith(MockitoExtension.class)
public class PermissioningPluginTest {
    @Mock
    private BlockchainService blockchain_service;

    @Mock
    private TransactionSimulationService txSimulation_service;

    @InjectMocks
    private PermissioningPlugin permissioningPlugin;

    @Test
    void testCheckConnectionAllowed_WhenConnectionIsAllowed() {
        // Arrange
        EnodeURL sourceEnode = mock(EnodeURL.class);
        EnodeURL destinationEnode = mock(EnodeURL.class);
        Hash mockChainHeadHash = Hash.fromHexString("0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef");
        TransactionSimulationResult simulationResult = mock(TransactionSimulationResult.class);
        Optional<TransactionSimulationResult> mockResult = Optional.of(simulationResult);

        when(blockchain_service.getChainHeadHash()).thenReturn(mockChainHeadHash);
        when(txSimulation_service.simulate(
            any(Transaction.class),
            any(),
            eq(mockChainHeadHash),
            any(OperationTracer.class),
            eq(false)))
            .thenReturn(mockResult);
        when(simulationResult.isSuccessful()).thenReturn(true);

        // Act
        boolean result = permissioningPlugin.checkConnectionAllowed(sourceEnode, destinationEnode);

        // Assert
        assertTrue(result);
        verify(blockchain_service).getChainHeadHash();
        verify(txSimulation_service).simulate(any(Transaction.class), any(), eq(mockChainHeadHash), any(OperationTracer.class), eq(false));
    }


    @Test
    void testCheckConnectionAllowed_WhenConnectionIsNotAllowed() {
        // Similar setup as above, but mock NodeConnectionSimulationReturnEval to return false
    }
}
