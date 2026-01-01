import React, { useState, useEffect, useCallback, useRef } from 'react';
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  ResponsiveContainer,
  AreaChart,
  Area,
  PieChart,
  Pie,
  Cell,
} from 'recharts';

const API_BASE_URL = 'http://localhost:9090';
const POLLING_INTERVAL = 5000;

const systemService = {
  async getSystemInfo() {
    try {
      const response = await fetch(`${API_BASE_URL}/public/torrents/systemInfo/getSystemStorage`, {
        method: 'GET',
        headers: { 'Content-Type': 'application/json' },
      });
      
      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }
      
      
      return await response.json();

    } catch (error) {
      console.warn('Failed to fetch system info, using fallback:', error);
      
      return {
        storage: {
          total: 1000, 
          used: 750,   
          available: 250 
        },
        network: {
          downloadSpeed: Math.random() * 30 + 5, 
          uploadSpeed: Math.random() * 10 + 2    
        }
      };
    }
  }
};

const torrentService = {
  async addTorrent(magnetLink) {
    const response = await fetch(`${API_BASE_URL}/public/torrents/add`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ magnet: magnetLink }),
    });
    
    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }
    
    return await response.json();
  },

  async getTorrents() {
    const response = await fetch(`${API_BASE_URL}/public/torrents/get`, {
      method: 'GET',
      headers: { 'Content-Type': 'application/json' },
    });
    
    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }
    
    return response.json();
  },

  async pauseTorrent(torrentId) {
    const response = await fetch(`${API_BASE_URL}/public/torrents/${torrentId}/pause`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
    });
    
    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }
    
    return response.json();
  },

  async resumeTorrent(torrentId) {
    const response = await fetch(`${API_BASE_URL}/public/torrents/${torrentId}/resume`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
    });
    
    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }
    
    return response.json();
  },

  async stopTorrent(torrentId) {
    const response = await fetch(`${API_BASE_URL}/public/torrents/${torrentId}/stop`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
    });
    
    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }
    
    return response.json();
  },

  async removeTorrent(torrentId) {
    const response = await fetch(`${API_BASE_URL}/public/torrents/${torrentId}/removeTorrent`, {
      method: 'DELETE',
      headers: { 'Content-Type': 'application/json' },
    });
    
    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }
    
    return response.json();
  },

  async getCompletedTorrents() {
    const response = await fetch(`${API_BASE_URL}/public/torrents/getCompleted`, {
      method: 'GET',
      headers: { 'Content-Type': 'application/json' },
    });


      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
        
      }

      return response.json();
  }

};

const getStatusColor = (status) => {
  const colors = {
    Downloading: 'text-blue-400 bg-blue-400/10',
    Seeding: 'text-emerald-400 bg-emerald-400/10',
    Paused: 'text-yellow-400 bg-yellow-400/10',
    Error: 'text-red-400 bg-red-400/10',
    Stopped: 'text-gray-400 bg-gray-400/10',
    Completed: 'text-emerald-400 bg-emerald-400/10',
  };
  return colors[status] || 'text-gray-400 bg-gray-400/10';
};

const getProgressColor = (status) => {
  const colors = {
    Downloading: 'bg-blue-500',
    Seeding: 'bg-emerald-500',
    Paused: 'bg-yellow-500',
    Error: 'bg-red-500',
    Stopped: 'bg-gray-500',
    Completed: 'bg-emerald-500',
  };
  return colors[status] || 'bg-gray-500';
};

const parseSpeed = (speedString) => {
  if (!speedString || typeof speedString !== "string") return 0;
  return parseFloat(speedString.replace(" MB/s", ""));
};

const formatBytes = (bytes) => {
  const gb = bytes / (1024 * 1024 * 1024);
  return `${gb.toFixed(1)} GB`;
};

const TorrentCard = ({ torrent, onRemove, onPause, onResume, onStop }) => {
  const handlePauseResume = () => {
    if (torrent.status === 'Paused') {
      onResume(torrent.id);
    } else if (['Downloading', 'Seeding'].includes(torrent.status)) {
      onPause(torrent.id);
    }
  };

  const canPauseResume = ['Downloading', 'Seeding', 'Paused'].includes(torrent.status);
  const canStop = !['Stopped', 'Error', 'Completed'].includes(torrent.status);

  return (
    <div className="bg-gradient-to-br from-slate-800/80 to-slate-900/90 backdrop-blur-lg border border-white/10 rounded-2xl p-4 hover:border-white/20 transition-all duration-300">
      <div className="flex justify-between items-start mb-3">
        <h3 className="text-white font-semibold text-sm truncate flex-1 mr-2">
          {torrent.title}
        </h3>
        <div className="flex gap-1 flex-shrink-0">
          {canPauseResume && (
            <button 
              onClick={handlePauseResume}
              className="text-gray-400 hover:text-yellow-400 transition-colors text-sm p-1 rounded hover:bg-yellow-400/10"
              title={torrent.status === 'Paused' ? 'Resume' : 'Pause'}
            >
              {torrent.status === 'Paused' ? '▶️' : '⏸️'}
            </button>
          )}
          {canStop && (
            <button 
              onClick={() => onStop(torrent.id)}
              className="text-gray-400 hover:text-red-400 transition-colors text-sm p-1 rounded hover:bg-red-400/10"
              title="Stop"
            >
              ⏹️
            </button>
          )}
          <button 
            onClick={() => onRemove(torrent.id)}
            className="text-gray-400 hover:text-red-400 transition-colors text-lg p-1 rounded hover:bg-red-400/10"
            title="Remove"
          >
            ✕
          </button>
        </div>
      </div>
      
      <div className="flex items-center gap-2 mb-3">
        <span className={`px-2 py-1 rounded-full text-xs font-medium ${getStatusColor(torrent.status)}`}>
          {torrent.status}
        </span>
        <span className="text-gray-400 text-xs">{torrent.size}</span>
      </div>

      <div className="mb-3">
        <div className="flex justify-between text-xs text-gray-400 mb-1">
          <span>Progress</span>
          <span>{torrent.progress}%</span>
        </div>
        <div className="w-full bg-slate-700/50 rounded-full h-2">
          <div 
            className={`h-2 rounded-full transition-all duration-300 ${getProgressColor(torrent.status)}`} 
            style={{width: `${torrent.progress}%`}}
          />
        </div>
      </div>

      <div className="flex justify-between text-xs">
        <div>
          <span className="text-gray-400">↓ </span>
          <span className="text-emerald-400 font-medium">{torrent.downloadSpeed}</span>
        </div>
        <div>
          <span className="text-gray-400">↑ </span>
          <span className="text-blue-400 font-medium">{torrent.uploadSpeed}</span>
        </div>
        <div>
          <span className="text-gray-400">Peers: </span>
          <span className="text-white">{torrent.peers}</span>
        </div>
      </div>

      {torrent.eta && (
        <div className="mt-2 text-xs text-gray-400">
          ETA: {torrent.eta}
        </div>
      )}
    </div>
  );
};

const AddTorrentCard = ({ onAdd, isLoading }) => {
  const [showForm, setShowForm] = useState(false);
  const [torrentUrl, setTorrentUrl] = useState('');

  const handleSubmit = () => {
    const url = torrentUrl.trim();
    if (url) {
      onAdd(url);
      setTorrentUrl('');
      setShowForm(false);
    }
  };

  const handleKeyPress = (e) => {
    if (e.key === 'Enter') {
      handleSubmit();
    }
  };

  if (showForm) {
    return (
      <div className="bg-gradient-to-br from-slate-800/80 to-slate-900/90 backdrop-blur-lg border border-white/10 rounded-2xl p-4">
        <input
          type="text"
          value={torrentUrl}
          onChange={(e) => setTorrentUrl(e.target.value)}
          placeholder="Enter torrent URL or magnet link..."
          className="w-full bg-slate-700/50 border border-white/10 rounded-lg px-3 py-2 text-white text-sm mb-3 focus:outline-none focus:border-blue-400"
          autoFocus
          onKeyPress={handleKeyPress}
          disabled={isLoading}
        />
        <div className="flex gap-2">
          <button
            onClick={handleSubmit}
            disabled={isLoading}
            className="flex-1 bg-blue-500 hover:bg-blue-600 disabled:bg-blue-300 text-white py-2 px-4 rounded-lg text-sm transition-colors"
          >
            {isLoading ? 'Adding...' : 'Add Torrent'}
          </button>
          <button
            onClick={() => {setShowForm(false); setTorrentUrl('');}}
            disabled={isLoading}
            className="px-4 py-2 text-gray-400 hover:text-white transition-colors text-sm"
          >
            Cancel
          </button>
        </div>
      </div>
    );
  }

  return (
    <button
      onClick={() => setShowForm(true)}
      className="bg-gradient-to-br from-slate-800/40 to-slate-900/60 backdrop-blur-lg border border-dashed border-white/20 rounded-2xl p-6 hover:border-white/40 transition-all duration-300 w-full flex items-center justify-center min-h-[200px]"
    >
      <div className="text-center">
        <div className="text-4xl text-gray-400 mb-2">+</div>
        <div className="text-gray-400 text-sm">Add New Torrent</div>
      </div>
    </button>
  );
};

const SystemStatsCard = ({ title, value, subtitle, color }) => (
  <div className="bg-gradient-to-br from-slate-800/80 to-slate-900/90 backdrop-blur-lg border border-white/10 rounded-2xl p-4">
    <h3 className="text-white font-bold text-sm mb-2">{title}</h3>
    <div className={`text-2xl font-bold ${color} mb-1`}>{value}</div>
    <div className="text-gray-400 text-xs">{subtitle}</div>
  </div>
);

const LoadingOverlay = ({ message = 'Loading...' }) => (
  <div className="fixed inset-0 bg-black/50 backdrop-blur-sm flex items-center justify-center z-50">
    <div className="bg-slate-800 rounded-lg p-6 text-white text-center">
      <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-400 mx-auto mb-4" />
      <p>{message}</p>
    </div>
  </div>
);


const useSystemMonitoring = () => {
  const [systemData, setSystemData] = useState({
    storage: { total: 1000, used: 750, available: 250 },
    network: { downloadSpeed: 0, uploadSpeed: 0 }
  });
  const [networkHistory, setNetworkHistory] = useState([]);
  const intervalRef = useRef(null);

  const updateSystemData = useCallback(async () => {
    try {
      const data = await systemService.getSystemInfo();
      setSystemData(data);

     
      const now = new Date();
      const timeString = now.toLocaleTimeString('en-US', { 
        hour12: false, 
        hour: '2-digit', 
        minute: '2-digit' 
      });

      setNetworkHistory(prev => {
        const newHistory = [...prev, {
          time: timeString,
          speed: data.network.downloadSpeed
        }];
        
       
        return newHistory.slice(-20);
      });
    } catch (error) {
      console.error('Failed to fetch system data:', error);
    }
  }, []);

  useEffect(() => {
    
    updateSystemData();

    
    intervalRef.current = setInterval(updateSystemData, POLLING_INTERVAL);

    return () => {
      if (intervalRef.current) {
        clearInterval(intervalRef.current);
      }
    };
  }, [updateSystemData]);

  return { systemData, networkHistory };
};

const useTorrents = () => {
  const [torrents, setTorrents] = useState([]);
  const [completedTorrents, setCompletedTorrents] = useState([]);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState(null);

  const handleError = useCallback((error, action) => {
    console.error(`Failed to ${action}:`, error);
    setError(`Failed to ${action}: ${error.message}`);
    setTimeout(() => setError(null), 5000);
  }, []);

  const loadTorrents = useCallback(async () => {
    try {
      const data = await torrentService.getTorrents();
      
      
      torrents.forEach(oldTorrent => {
        const newTorrent = data.find(t => t.id === oldTorrent.id);
        if (newTorrent && 
            oldTorrent.progress < 100 && 
            newTorrent.progress === 100 && 
            newTorrent.status !== 'Seeding') {
          
          
          setCompletedTorrents(prev => [{
            id: newTorrent.id,
            title: newTorrent.title,
            size: newTorrent.size,
            completedAt: 'Just now'
          }, ...prev.slice(0, 9)]); 
        }
      });

      setTorrents(data);
    } catch (error) {
      handleError(error, 'load torrents');
    }
  }, [handleError, torrents]);

  const loadCompletedTorrents = useCallback(async () => {
  try {
    const data = await torrentService.getCompletedTorrents();
    setCompletedTorrents(data);
  } catch (error) {
    handleError(error, 'load completed torrents');
  }
}, [handleError]);

useEffect(() => {
  loadCompletedTorrents(); 
  loadTorrents();
  const interval = setInterval(loadTorrents, POLLING_INTERVAL);
  return () => clearInterval(interval);
}, [loadTorrents, loadCompletedTorrents]);


  const addTorrent = useCallback(async (magnetLink) => {
    setIsLoading(true);
    try {
      const data = await torrentService.addTorrent(magnetLink);
      setTorrents(prev => [...prev, data]);
      setError(null);
    } catch (error) {
      handleError(error, 'add torrent');
    } finally {
      setIsLoading(false);
    }
  }, [handleError]);

  const updateTorrentStatus = useCallback((id, updates) => {
    setTorrents(prev => prev.map(t => t.id === id ? { ...t, ...updates } : t));
  }, []);

  const pauseTorrent = useCallback(async (id) => {
    try {
      await torrentService.pauseTorrent(id);
      updateTorrentStatus(id, { 
        status: 'Paused', 
        downloadSpeed: '0 MB/s', 
        uploadSpeed: '0 MB/s', 
        eta: null 
      });
    } catch (error) {
      handleError(error, 'pause torrent');
    }
  }, [handleError, updateTorrentStatus]);

  const resumeTorrent = useCallback(async (id) => {
    try {
      await torrentService.resumeTorrent(id);
      const torrent = torrents.find(t => t.id === id);
      updateTorrentStatus(id, { 
        status: torrent?.progress === 100 ? 'Seeding' : 'Downloading', 
        eta: torrent?.progress < 100 ? 'Calculating...' : null 
      });
    } catch (error) {
      handleError(error, 'resume torrent');
    }
  }, [handleError, updateTorrentStatus, torrents]);

  const stopTorrent = useCallback(async (id) => {
    try {
      await torrentService.stopTorrent(id);
      updateTorrentStatus(id, { 
        status: 'Stopped', 
        downloadSpeed: '0 MB/s', 
        uploadSpeed: '0 MB/s', 
        eta: null 
      });
    } catch (error) {
      handleError(error, 'stop torrent');
    }
  }, [handleError, updateTorrentStatus]);

  const removeTorrent = useCallback(async (id) => {
    try {
      await torrentService.removeTorrent(id);
      setTorrents(prev => prev.filter(t => t.id !== id));
    } catch (error) {
      handleError(error, 'remove torrent');
    }
  }, [handleError]);

  return {
    torrents,
    completedTorrents,
    isLoading,
    error,
    addTorrent,
    pauseTorrent,
    resumeTorrent,
    stopTorrent,
    removeTorrent,
    loadTorrents
  };
};

const Dashboard = () => {
  const {
    torrents,
    completedTorrents,
    isLoading,
    error,
    addTorrent,
    pauseTorrent,
    resumeTorrent,
    stopTorrent,
    removeTorrent
  } = useTorrents();

  const { systemData, networkHistory } = useSystemMonitoring();

  
  useEffect(() => {
    if (torrents.length === 0) {
      
    }
  }, [torrents.length]);

  const downloadingTorrents = torrents.filter(t => t.status === 'Downloading');
  const seedingTorrents = torrents.filter(t => t.status === 'Seeding');
  
  const totalDownloadSpeed = downloadingTorrents
    .reduce((sum, t) => sum + parseSpeed(t.downloadSpeed), 0);
  
  const totalUploadSpeed = torrents
    .reduce((sum, t) => sum + parseSpeed(t.uploadSpeed), 0);

  
  const storageData = [
    { name: 'Used', value: systemData.storage.used, color: '#ef4444' },
    { name: 'Available', value: systemData.storage.available, color: '#10b981' },
  ];

  return (
    <div className="min-h-screen w-screen bg-gradient-to-br from-slate-950 via-blue-950 to-slate-950 overflow-x-hidden">
      {isLoading && <LoadingOverlay message="Adding torrent..." />}
      
      <div className="w-full h-full p-4 lg:p-6">
        <div className="mb-6">
          <h1 className="text-3xl font-bold text-white mb-2">Agora essa merda vai funcionar</h1>
          <p className="text-gray-400 text-sm">Monitor and manage your torrent downloads</p>
          {error && (
            <div className="mt-2 p-3 bg-red-500/10 border border-red-500/20 rounded-lg text-red-400 text-sm">
              {error}
            </div>
          )}
        </div>

        <div className="flex flex-col lg:flex-row gap-4 lg:gap-6 w-full">
          <div className="flex-1 lg:flex-[3] space-y-4 w-full">
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 w-full">
              <SystemStatsCard
                title="Total Download"
                value={`${totalDownloadSpeed.toFixed(2)} MB/s`}
                subtitle={`${downloadingTorrents.length} active downloads`}
                color="text-emerald-400"
              />
              <SystemStatsCard
                title="Total Upload"
                value={`${totalUploadSpeed.toFixed(2)} MB/s`}
                subtitle={`${seedingTorrents.length} seeding`}
                color="text-blue-400"
              />
            </div>

            <div className="grid grid-cols-1 xl:grid-cols-2 2xl:grid-cols-3 gap-4 w-full">
              {torrents.map((torrent) => (
                <TorrentCard 
                  key={torrent.id} 
                  torrent={torrent} 
                  onRemove={removeTorrent}
                  onPause={pauseTorrent}
                  onResume={resumeTorrent}
                  onStop={stopTorrent}
                />
              ))}
              <AddTorrentCard onAdd={addTorrent} isLoading={isLoading} />
            </div>
          </div>

          <div className="w-full lg:w-80 xl:w-96 flex-shrink-0 space-y-4">
            <div className="bg-gradient-to-br from-slate-800/80 to-slate-900/90 backdrop-blur-lg border border-white/10 rounded-2xl p-4 w-full">
              <h3 className="text-white font-bold text-sm mb-3">Network Speed</h3>
              <div className="h-32 w-full">
                <ResponsiveContainer width="100%" height="100%">
                  <AreaChart data={networkHistory}>
                    <defs>
                      <linearGradient id="colorSpeed" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="5%" stopColor="#10b981" stopOpacity={0.3}/>
                        <stop offset="95%" stopColor="#10b981" stopOpacity={0}/>
                      </linearGradient>
                    </defs>
                    <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.1)" />
                    <XAxis dataKey="time" stroke="rgba(255,255,255,0.5)" fontSize={10} />
                    <YAxis stroke="rgba(255,255,255,0.5)" fontSize={10} />
                    <Area
                      type="monotone"
                      dataKey="speed"
                      stroke="#10b981"
                      strokeWidth={2}
                      fill="url(#colorSpeed)"
                    />
                  </AreaChart>
                </ResponsiveContainer>
              </div>
              <div className="mt-2 flex justify-between text-xs text-gray-400">
                <span>↓ {systemData.network.downloadSpeed.toFixed(1)} MB/s</span>
                <span>↑ {systemData.network.uploadSpeed.toFixed(1)} MB/s</span>
              </div>
            </div>

            <div className="bg-gradient-to-br from-slate-800/80 to-slate-900/90 backdrop-blur-lg border border-white/10 rounded-2xl p-4 w-full">
              <h3 className="text-white font-bold text-sm mb-3">Storage Space</h3>
              <div className="flex items-center justify-center mb-3">
                <div className="w-24 h-24">
                  <ResponsiveContainer width="100%" height="100%">
                    <PieChart>
                      <Pie
                        data={storageData}
                        cx="50%"
                        cy="50%"
                        innerRadius={25}
                        outerRadius={45}
                        paddingAngle={5}
                        dataKey="value"
                      >
                        {storageData.map((entry, index) => (
                          <Cell key={`cell-${index}`} fill={entry.color} />
                        ))}
                      </Pie>
                    </PieChart>
                  </ResponsiveContainer>
                </div>
              </div>
              <div className="grid grid-cols-2 gap-2 mb-2">
                <div className="text-center">
                  <div className="text-red-400 font-bold text-sm">{formatBytes(systemData.storage.used * 1024 * 1024 * 1024)}</div>
                  <div className="text-gray-400 text-xs">Used</div>
                </div>
                <div className="text-center">
                  <div className="text-emerald-400 font-bold text-sm">{formatBytes(systemData.storage.available * 1024 * 1024 * 1024)}</div>
                  <div className="text-gray-400 text-xs">Available</div>
                </div>
              </div>
              <div className="text-center">
                <div className="text-gray-300 text-xs">Total: {formatBytes(systemData.storage.total * 1024 * 1024 * 1024)}</div>
              </div>
            </div>

            <div className="bg-gradient-to-br from-slate-800/80 to-slate-900/90 backdrop-blur-lg border border-white/10 rounded-2xl p-4 w-full">
              <h3 className="text-white font-bold text-sm mb-3">Completed Downloads</h3>
              <div className="space-y-2 overflow-y-auto custom-scrollbar max-h-60">
                {completedTorrents.map((torrent) => (
                  <div key={torrent.id} className="flex items-center gap-2 p-2 bg-slate-700/30 rounded-lg hover:bg-slate-700/50 transition-colors">
                    <div className="w-2 h-2 bg-emerald-500 rounded-full flex-shrink-0" />
                    <div className="flex-1 min-w-0">
                      <div className="text-white text-xs font-medium truncate">{torrent.title}</div>
                      <div className="text-gray-400 text-xs">{torrent.size} • {torrent.completedAt}</div>
                    </div>
                    <div className="text-emerald-400 text-xs flex-shrink-0">✓</div>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </div>
      </div>

      <style>{`
        .custom-scrollbar::-webkit-scrollbar {
          width: 4px;
        }
        .custom-scrollbar::-webkit-scrollbar-track {
          background: rgba(255, 255, 255, 0.1);
          border-radius: 2px;
        }
        .custom-scrollbar::-webkit-scrollbar-thumb {
          background: rgba(255, 255, 255, 0.3);
          border-radius: 2px;
        }
        .custom-scrollbar::-webkit-scrollbar-thumb:hover {
          background: rgba(255, 255, 255, 0.5);
        }
      `}</style>
    </div>
  );
};

export default Dashboard;