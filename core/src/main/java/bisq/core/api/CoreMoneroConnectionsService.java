package bisq.core.api;

import bisq.common.UserThread;
import bisq.core.btc.model.EncryptedConnectionList;
import bisq.core.btc.setup.DownloadListener;
import bisq.core.btc.setup.WalletsSetup;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleObjectProperty;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import monero.common.MoneroConnectionManager;
import monero.common.MoneroConnectionManagerListener;
import monero.common.MoneroRpcConnection;
import monero.daemon.MoneroDaemon;
import monero.daemon.MoneroDaemonRpc;
import monero.daemon.model.MoneroPeer;

@Slf4j
@Singleton
public final class CoreMoneroConnectionsService {

    private static final int MIN_BROADCAST_CONNECTIONS = 0; // TODO: 0 for stagenet, 5+ for mainnet
    private static final long DAEMON_REFRESH_PERIOD_MS = 15000L; // check connection periodically in ms
    private static final long DAEMON_INFO_POLL_PERIOD_MS = 20000L; // collect daemon info periodically in ms

    // TODO (woodser): support each network type, move to config, remove localhost authentication
    private static final List<MoneroRpcConnection> DEFAULT_CONNECTIONS = Arrays.asList(
            new MoneroRpcConnection("http://localhost:38081", "superuser", "abctesting123").setPriority(1), // localhost is first priority
            new MoneroRpcConnection("http://haveno.exchange:38081", "", "").setPriority(2)
    );

    private final Object lock = new Object();
    private final CoreAccountService accountService;
    private final MoneroConnectionManager connectionManager;
    private final EncryptedConnectionList connectionList;
    private final ObjectProperty<List<MoneroPeer>> peers = new SimpleObjectProperty<>();
    private final IntegerProperty numPeers = new SimpleIntegerProperty(0);
    private final LongProperty chainHeight = new SimpleLongProperty(0);
    private final DownloadListener downloadListener = new DownloadListener();

    private MoneroDaemon daemon;
    private boolean isInitialized = false;

    @Inject
    public CoreMoneroConnectionsService(WalletsSetup walletsSetup,
                                        CoreAccountService accountService,
                                        MoneroConnectionManager connectionManager,
                                        EncryptedConnectionList connectionList) {
        this.accountService = accountService;
        this.connectionManager = connectionManager;
        this.connectionList = connectionList;

        // initialize after account open and basic setup
        walletsSetup.addSetupTaskHandler(() -> { // TODO: use something better than legacy WalletSetup for notification to initialize
            
            // initialize from connections read from disk
            initialize();
            
            // listen for account to be opened or password changed
            accountService.addListener(new AccountServiceListener() {
                
                @Override
                public void onAccountOpened() {
                    try {
                        log.info(getClass() + ".onAccountOpened() called");
                        initialize();
                    } catch (Exception e) {
                        e.printStackTrace();
                        throw new RuntimeException(e);
                    }
                }
                
                @Override
                public void onPasswordChanged(String oldPassword, String newPassword) {
                    log.info(getClass() + ".onPasswordChanged({}, {}) called", oldPassword, newPassword);
                    connectionList.changePassword(oldPassword, newPassword);
                }
            });
        });
    }

    // ------------------------ CONNECTION MANAGEMENT -------------------------
    
    public MoneroDaemon getDaemon() {
        accountService.checkAccountOpen();
        return this.daemon;
    }
    
    public void addListener(MoneroConnectionManagerListener listener) {
        synchronized (lock) {
            accountService.checkAccountOpen();
            connectionManager.addListener(listener);
        }
    }

    public void addConnection(MoneroRpcConnection connection) {
        synchronized (lock) {
            accountService.checkAccountOpen();
            connectionList.addConnection(connection);
            connectionManager.addConnection(connection);
        }
    }

    public void removeConnection(String uri) {
        synchronized (lock) {
            accountService.checkAccountOpen();
            connectionList.removeConnection(uri);
            connectionManager.removeConnection(uri);
        }
    }

    public MoneroRpcConnection getConnection() {
        synchronized (lock) {
            accountService.checkAccountOpen();
            return connectionManager.getConnection();
        }
    }

    public List<MoneroRpcConnection> getConnections() {
        synchronized (lock) {
            accountService.checkAccountOpen();
            return connectionManager.getConnections();
        }
    }

    public void setConnection(String connectionUri) {
        synchronized (lock) {
            accountService.checkAccountOpen();
            connectionManager.setConnection(connectionUri); // listener will update connection list
        }
    }

    public void setConnection(MoneroRpcConnection connection) {
        synchronized (lock) {
            accountService.checkAccountOpen();
            connectionManager.setConnection(connection); // listener will update connection list
        }
    }

    public MoneroRpcConnection checkConnection() {
        synchronized (lock) {
            accountService.checkAccountOpen();
            connectionManager.checkConnection();
            return getConnection();
        }
    }

    public List<MoneroRpcConnection> checkConnections() {
        synchronized (lock) {
            accountService.checkAccountOpen();
            connectionManager.checkConnections();
            return getConnections();
        }
    }

    public void startCheckingConnection(Long refreshPeriod) {
        synchronized (lock) {
            accountService.checkAccountOpen();
            connectionManager.startCheckingConnection(refreshPeriod == null ? DAEMON_REFRESH_PERIOD_MS : refreshPeriod);
            connectionList.setRefreshPeriod(refreshPeriod);
        }
    }

    public void stopCheckingConnection() {
        synchronized (lock) {
            accountService.checkAccountOpen();
            connectionManager.stopCheckingConnection();
            connectionList.setRefreshPeriod(-1L);
        }
    }

    public MoneroRpcConnection getBestAvailableConnection() {
        synchronized (lock) {
            accountService.checkAccountOpen();
            return connectionManager.getBestAvailableConnection();
        }
    }

    public void setAutoSwitch(boolean autoSwitch) {
        synchronized (lock) {
            accountService.checkAccountOpen();
            connectionManager.setAutoSwitch(autoSwitch);
            connectionList.setAutoSwitch(autoSwitch);
        }
    }
    
    // ----------------------------- APP METHODS ------------------------------
    
    public boolean isChainHeightSyncedWithinTolerance() {
        if (daemon == null) return false;
        Long targetHeight = daemon.getSyncInfo().getTargetHeight();
        if (targetHeight == 0) return true; // monero-daemon-rpc sync_info's target_height returns 0 when node is fully synced
        long currentHeight = chainHeight.get();
        if (Math.abs(targetHeight - currentHeight) <= 3) {
            return true;
        }
        log.warn("Our chain height: {} is out of sync with peer nodes chain height: {}", chainHeight.get(), targetHeight);
        return false;
    }
    
    public ReadOnlyIntegerProperty numPeersProperty() {
        return numPeers;
    }

    public ReadOnlyObjectProperty<List<MoneroPeer>> peerConnectionsProperty() {
        return peers;
    }
    
    public boolean hasSufficientPeersForBroadcast() {
        return numPeers.get() >= getMinBroadcastConnections();
    }

    public LongProperty chainHeightProperty() {
        return chainHeight;
    }
    
    public ReadOnlyDoubleProperty downloadPercentageProperty() {
        return downloadListener.percentageProperty();
    }
    
    public int getMinBroadcastConnections() {
        return MIN_BROADCAST_CONNECTIONS;
    }
    
    public boolean isDownloadComplete() {
        return downloadPercentageProperty().get() == 1d;
    }
    
    /**
     * Signals that both the daemon and wallet have synced.
     * 
     * TODO: separate daemon and wallet download/done listeners
     */
    public void doneDownload() {
        downloadListener.doneDownload();
    }
    
    // ------------------------------- HELPERS --------------------------------
    
    private void initialize() {
        synchronized (lock) {
            
            // reset connection manager's connections and listeners
            connectionManager.reset();

            // load connections
            connectionList.getConnections().forEach(connectionManager::addConnection);
            log.info("Read " + connectionList.getConnections().size() + " connections from disk");

            // add default connections
            for (MoneroRpcConnection connection : DEFAULT_CONNECTIONS) {
                if (connectionList.hasConnection(connection.getUri())) continue;
                addConnection(connection);
            }

            // restore last used connection
            connectionList.getCurrentConnectionUri().ifPresentOrElse(connectionManager::setConnection, () -> {
                connectionManager.setConnection(DEFAULT_CONNECTIONS.get(0).getUri()); // default to localhost
            });
            
            // initialize daemon
            daemon = new MoneroDaemonRpc(connectionManager.getConnection());
            updateDaemonInfo();

            // restore configuration
            connectionManager.setAutoSwitch(connectionList.getAutoSwitch());
            long refreshPeriod = connectionList.getRefreshPeriod();
            if (refreshPeriod > 0) connectionManager.startCheckingConnection(refreshPeriod);
            else if (refreshPeriod == 0) connectionManager.startCheckingConnection(DAEMON_REFRESH_PERIOD_MS);
            else checkConnection();

            // run once
            if (!isInitialized) {

                // register connection change listener
                connectionManager.addListener(this::onConnectionChanged);

                // poll daemon periodically
                startPollingDaemon();
                isInitialized = true;
            }
        }
    }
    
    private void onConnectionChanged(MoneroRpcConnection currentConnection) {
        synchronized (lock) {
            if (currentConnection == null) {
                daemon = null;
                connectionList.setCurrentConnectionUri(null);
            } else {
                daemon = new MoneroDaemonRpc(connectionManager.getConnection());
                connectionList.removeConnection(currentConnection.getUri());
                connectionList.addConnection(currentConnection);
                connectionList.setCurrentConnectionUri(currentConnection.getUri());
            }
        }
    }

    private void startPollingDaemon() {
        UserThread.runPeriodically(() -> {
            updateDaemonInfo();
        }, DAEMON_INFO_POLL_PERIOD_MS / 1000l);
    }

    private void updateDaemonInfo() {
        try {
            if (daemon == null) throw new RuntimeException("No daemon connection");
            peers.set(getOnlinePeers());
            numPeers.set(peers.get().size());
            chainHeight.set(daemon.getHeight());
        } catch (Exception e) {
            log.warn("Could not update daemon info: " + e.getMessage());
        }
    }

    private List<MoneroPeer> getOnlinePeers() {
        return daemon.getPeers().stream()
                .filter(peer -> peer.isOnline())
                .collect(Collectors.toList());
    }
}
