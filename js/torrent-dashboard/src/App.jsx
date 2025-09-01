import React, { useState } from 'react';
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

// Sample data for charts
const downloadSpeedData = [
  { time: '00:00', speed: 5.2 },
  { time: '00:05', speed: 8.1 },
  { time: '00:10', speed: 12.5 },
  { time: '00:15', speed: 15.8 },
  { time: '00:20', speed: 18.3 },
  { time: '00:25', speed: 22.1 },
  { time: '00:30', speed: 25.4 },
  { time: '00:35', speed: 28.7 },
  { time: '00:40', speed: 32.2 },
  { time: '00:45', speed: 29.8 },
];

const systemSpaceData = [
  { name: 'Used', value: 750, color: '#ef4444' },
  { name: 'Available', value: 250, color: '#10b981' },
];

const TorrentCard = ({ torrent, onRemove, onPause, onResume, onStop }) => {
  const getStatusColor = (status) => {
    switch (status) {
      case 'Downloading': return 'text-blue-400 bg-blue-400/10';
      case 'Seeding': return 'text-emerald-400 bg-emerald-400/10';
      case 'Paused': return 'text-yellow-400 bg-yellow-400/10';
      case 'Error': return 'text-red-400 bg-red-400/10';
      case 'Stopped': return 'text-gray-400 bg-gray-400/10';
      default: return 'text-gray-400 bg-gray-400/10';
    }
  };

  const getProgressColor = (status) => {
    switch (status) {
      case 'Downloading': return 'bg-blue-500';
      case 'Seeding': return 'bg-emerald-500';
      case 'Paused': return 'bg-yellow-500';
      case 'Error': return 'bg-red-500';
      case 'Stopped': return 'bg-gray-500';
      default: return 'bg-gray-500';
    }
  };

  const handlePauseResume = () => {
    if (torrent.status === 'Paused') {
      onResume(torrent.id);
    } else if (torrent.status === 'Downloading' || torrent.status === 'Seeding') {
      onPause(torrent.id);
    }
  };

  const canPauseResume = torrent.status === 'Downloading' || torrent.status === 'Seeding' || torrent.status === 'Paused';
  const canStop = torrent.status !== 'Stopped' && torrent.status !== 'Error';

  return (
    <div className="bg-gradient-to-br from-slate-800/80 to-slate-900/90 backdrop-blur-lg border border-white/10 rounded-2xl p-4 hover:border-white/20 transition-all duration-300">
      <div className="flex justify-between items-start mb-3">
        <h3 className="text-white font-semibold text-sm truncate flex-1 mr-2">{torrent.title}</h3>
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
          ></div>
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

const AddTorrentCard = ({ onAdd }) => {
  const [showForm, setShowForm] = useState(false);
  const [torrentUrl, setTorrentUrl] = useState('');

  const handleSubmit = (e) => {
    if (torrentUrl.trim()) {
      onAdd(torrentUrl.trim());
      setTorrentUrl('');
      setShowForm(false);
    }
  };

  if (showForm) {
    return (
      <div className="bg-gradient-to-br from-slate-800/80 to-slate-900/90 backdrop-blur-lg border border-white/10 rounded-2xl p-4">
        <div>
          <input
            type="text"
            value={torrentUrl}
            onChange={(e) => setTorrentUrl(e.target.value)}
            placeholder="Enter torrent URL or magnet link..."
            className="w-full bg-slate-700/50 border border-white/10 rounded-lg px-3 py-2 text-white text-sm mb-3 focus:outline-none focus:border-blue-400"
            autoFocus
            onKeyPress={(e) => {
              if (e.key === 'Enter') {
                handleSubmit(e);
              }
            }}
          />
          <div className="flex gap-2">
            <button
              onClick={handleSubmit}
              className="flex-1 bg-blue-500 hover:bg-blue-600 text-white py-2 px-4 rounded-lg text-sm transition-colors"
            >
              Add Torrent
            </button>
            <button
              onClick={() => {setShowForm(false); setTorrentUrl('');}}
              className="px-4 py-2 text-gray-400 hover:text-white transition-colors text-sm"
            >
              Cancel
            </button>
          </div>
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

const CompletedTorrentsList = ({ completedTorrents }) => (
  <div className="bg-gradient-to-br from-slate-800/80 to-slate-900/90 backdrop-blur-lg border border-white/10 rounded-2xl p-4 h-full">
    <h3 className="text-white font-bold text-lg mb-4">Completed Downloads</h3>
    <div className="space-y-3 overflow-y-auto custom-scrollbar max-h-80">
      {completedTorrents.map((torrent) => (
        <div key={torrent.id} className="flex items-center gap-3 p-2 bg-slate-700/30 rounded-lg hover:bg-slate-700/50 transition-colors">
          <div className="w-2 h-2 bg-emerald-500 rounded-full flex-shrink-0"></div>
          <div className="flex-1 min-w-0">
            <div className="text-white text-sm font-medium truncate">{torrent.title}</div>
            <div className="text-gray-400 text-xs">{torrent.size} • {torrent.completedAt}</div>
          </div>
          <div className="text-emerald-400 text-xs flex-shrink-0">✓</div>
        </div>
      ))}
    </div>
  </div>
);

const SystemStatsCard = ({ title, value, subtitle, color }) => (
  <div className="bg-gradient-to-br from-slate-800/80 to-slate-900/90 backdrop-blur-lg border border-white/10 rounded-2xl p-4">
    <h3 className="text-white font-bold text-sm mb-2">{title}</h3>
    <div className={`text-2xl font-bold ${color} mb-1`}>{value}</div>
    <div className="text-gray-400 text-xs">{subtitle}</div>
  </div>
);


const torrentAPI = {

  addTorrent: async (magnetLink) => {
    try {
      const response = await fetch('http://localhost:9090/api/torrents', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ magnetLink }),
      });
      return await response.json();
    } catch (error) {
      console.error('Error adding torrent:', error);
      throw error;
    }
  },

  // Pause a torrent
  pauseTorrent: async (torrentId) => {
    try {
      const response = await fetch(`/api/torrents/${torrentId}/pause`, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
        },
      });
      return await response.json();
    } catch (error) {
      console.error('Error pausing torrent:', error);
      throw error;
    }
  },

  // Resume a torrent
  resumeTorrent: async (torrentId) => {
    try {
      const response = await fetch(`/api/torrents/${torrentId}/resume`, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
        },
      });
      return await response.json();
    } catch (error) {
      console.error('Error resuming torrent:', error);
      throw error;
    }
  },

  // Stop a torrent
  stopTorrent: async (torrentId) => {
    try {
      const response = await fetch(`/api/torrents/${torrentId}/stop`, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
        },
      });
      return await response.json();
    } catch (error) {
      console.error('Error stopping torrent:', error);
      throw error;
    }
  },

  // Remove/delete a torrent
  removeTorrent: async (torrentId) => {
    try {
      const response = await fetch(`/api/torrents/${torrentId}`, {
        method: 'DELETE',
        headers: {
          'Content-Type': 'application/json',
        },
      });
      return await response.json();
    } catch (error) {
      console.error('Error removing torrent:', error);
      throw error;
    }
  },

  // Get all torrents status
  getTorrents: async () => {
    try {
      const response = await fetch('/api/torrents', {
        method: 'GET',
        headers: {
          'Content-Type': 'application/json',
        },
      });
      return await response.json();
    } catch (error) {
      console.error('Error fetching torrents:', error);
      throw error;
    }
  },
};

const Dashboard = () => {
  const [torrents, setTorrents] = useState([
    {
      id: 1,
      title: "Ubuntu 22.04.3 Desktop amd64.iso",
      status: "Downloading",
      progress: 65,
      size: "4.7 GB",
      downloadSpeed: "15.2 MB/s",
      uploadSpeed: "2.1 MB/s",
      peers: 124,
      eta: "12m 34s"
    },
    {
      id: 2,
      title: "Big Buck Bunny 4K",
      status: "Seeding",
      progress: 100,
      size: "2.1 GB",
      downloadSpeed: "0 MB/s",
      uploadSpeed: "5.8 MB/s",
      peers: 45,
      eta: null
    },
    {
      id: 3,
      title: "Debian 12 netinst",
      status: "Stopped",
      progress: 45,
      size: "1.2 GB",
      downloadSpeed: "0 MB/s",
      uploadSpeed: "0 MB/s",
      peers: 0,
      eta: null
    },
    {
      id: 4,
      title: "Linux Mint 21.2 Cinnamon",
      status: "Downloading",
      progress: 89,
      size: "2.8 GB",
      downloadSpeed: "8.9 MB/s",
      uploadSpeed: "1.2 MB/s",
      peers: 67,
      eta: "3m 15s"
    }
  ]);

  const [completedTorrents] = useState([
    {
      id: 101,
      title: "Fedora 38 Workstation Live",
      size: "1.9 GB",
      completedAt: "2 hours ago"
    },
    {
      id: 102,
      title: "VLC Media Player 3.0.18",
      size: "45 MB",
      completedAt: "5 hours ago"
    },
    {
      id: 103,
      title: "OpenOffice 4.1.13",
      size: "132 MB",
      completedAt: "1 day ago"
    },
    {
      id: 104,
      title: "GIMP 2.10.34 Portable",
      size: "278 MB",
      completedAt: "2 days ago"
    },
    {
      id: 105,
      title: "Blender 4.0 LTS",
      size: "312 MB",
      completedAt: "3 days ago"
    }
  ]);

  // Torrent control functions with API calls
  const addTorrent = async (magnetLink) => {
    try {
      // Call API to add torrent to backend
      const result = await torrentAPI.addTorrent(magnetLink);
      
      // Add to local state (in real app, you'd get the torrent data from API response)
      const newTorrent = {
        id: result.id || Date.now(),
        title: result.title || `New Torrent ${torrents.length + 1}`,
        status: "Downloading",
        progress: 0,
        size: result.size || "Unknown",
        downloadSpeed: "0 MB/s",
        uploadSpeed: "0 MB/s",
        peers: 0,
        eta: "Calculating..."
      };
      setTorrents([...torrents, newTorrent]);
    } catch (error) {
      console.error('Failed to add torrent:', error);
      // Show error message to user
      alert('Failed to add torrent. Please try again.');
    }
  };

  const pauseTorrent = async (id) => {
    try {
      await torrentAPI.pauseTorrent(id);
      setTorrents(torrents.map(t => 
        t.id === id 
          ? { ...t, status: 'Paused', downloadSpeed: '0 MB/s', uploadSpeed: '0 MB/s', eta: null }
          : t
      ));
    } catch (error) {
      console.error('Failed to pause torrent:', error);
      alert('Failed to pause torrent. Please try again.');
    }
  };

  const resumeTorrent = async (id) => {
    try {
      await torrentAPI.resumeTorrent(id);
      setTorrents(torrents.map(t => 
        t.id === id 
          ? { ...t, status: t.progress === 100 ? 'Seeding' : 'Downloading', eta: t.progress < 100 ? 'Calculating...' : null }
          : t
      ));
    } catch (error) {
      console.error('Failed to resume torrent:', error);
      alert('Failed to resume torrent. Please try again.');
    }
  };

  const stopTorrent = async (id) => {
    try {
      await torrentAPI.stopTorrent(id);
      setTorrents(torrents.map(t => 
        t.id === id 
          ? { ...t, status: 'Stopped', downloadSpeed: '0 MB/s', uploadSpeed: '0 MB/s', eta: null }
          : t
      ));
    } catch (error) {
      console.error('Failed to stop torrent:', error);
      alert('Failed to stop torrent. Please try again.');
    }
  };

  const removeTorrent = async (id) => {
    try {
      await torrentAPI.removeTorrent(id);
      setTorrents(torrents.filter(t => t.id !== id));
    } catch (error) {
      console.error('Failed to remove torrent:', error);
      alert('Failed to remove torrent. Please try again.');
    }
  };

  const totalDownloadSpeed = torrents
    .filter(t => t.status === 'Downloading')
    .reduce((sum, t) => sum + parseFloat(t.downloadSpeed.replace(' MB/s', '') || 0), 0);

  const totalUploadSpeed = torrents
    .reduce((sum, t) => sum + parseFloat(t.uploadSpeed.replace(' MB/s', '') || 0), 0);

  return (
    <div className="min-h-screen w-screen bg-gradient-to-br from-slate-950 via-blue-950 to-slate-950 overflow-x-hidden">
      <div className="w-full h-full p-4 lg:p-6">
        {/* Header */}
        <div className="mb-6">
          <h1 className="text-3xl font-bold text-white mb-2">Torrent Manager</h1>
          <p className="text-gray-400 text-sm">Monitor and manage your torrent downloads</p>
        </div>

        {/* Main Layout */}
        <div className="flex flex-col lg:flex-row gap-4 lg:gap-6 w-full">
          
          {/* Left Section - Torrent Cards */}
          <div className="flex-1 lg:flex-[3] space-y-4 w-full">
            {/* System Stats Row */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 w-full">
              <SystemStatsCard
                title="Total Download"
                value={`${totalDownloadSpeed.toFixed(1)} MB/s`}
                subtitle={`${torrents.filter(t => t.status === 'Downloading').length} active downloads`}
                color="text-emerald-400"
              />
              <SystemStatsCard
                title="Total Upload"
                value={`${totalUploadSpeed.toFixed(1)} MB/s`}
                subtitle={`${torrents.filter(t => t.status === 'Seeding').length} seeding`}
                color="text-blue-400"
              />
            </div>

            {/* Torrent Cards Grid */}
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
              <AddTorrentCard onAdd={addTorrent} />
            </div>
          </div>

          {/* Right Section - Charts and Completed */}
          <div className="w-full lg:w-80 xl:w-96 flex-shrink-0 space-y-4">
            
            {/* Download Speed Chart */}
            <div className="bg-gradient-to-br from-slate-800/80 to-slate-900/90 backdrop-blur-lg border border-white/10 rounded-2xl p-4 w-full">
              <h3 className="text-white font-bold text-sm mb-3">Download Speed</h3>
              <div className="h-32 w-full">
                <ResponsiveContainer width="100%" height="100%">
                  <AreaChart data={downloadSpeedData}>
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
            </div>

            {/* System Space Chart */}
            <div className="bg-gradient-to-br from-slate-800/80 to-slate-900/90 backdrop-blur-lg border border-white/10 rounded-2xl p-4 w-full">
              <h3 className="text-white font-bold text-sm mb-3">Storage Space</h3>
              <div className="flex items-center justify-center mb-3">
                <div className="w-24 h-24">
                  <ResponsiveContainer width="100%" height="100%">
                    <PieChart>
                      <Pie
                        data={systemSpaceData}
                        cx="50%"
                        cy="50%"
                        innerRadius={25}
                        outerRadius={45}
                        paddingAngle={5}
                        dataKey="value"
                      >
                        {systemSpaceData.map((entry, index) => (
                          <Cell key={`cell-${index}`} fill={entry.color} />
                        ))}
                      </Pie>
                    </PieChart>
                  </ResponsiveContainer>
                </div>
              </div>
              <div className="grid grid-cols-2 gap-2">
                <div className="text-center">
                  <div className="text-red-400 font-bold text-sm">750 GB</div>
                  <div className="text-gray-400 text-xs">Used</div>
                </div>
                <div className="text-center">
                  <div className="text-emerald-400 font-bold text-sm">250 GB</div>
                  <div className="text-gray-400 text-xs">Available</div>
                </div>
              </div>
            </div>

            {/* Completed Torrents */}
            <div className="bg-gradient-to-br from-slate-800/80 to-slate-900/90 backdrop-blur-lg border border-white/10 rounded-2xl p-4 w-full">
              <h3 className="text-white font-bold text-sm mb-3">Completed Downloads</h3>
              <div className="space-y-2 overflow-y-auto custom-scrollbar max-h-60">
                {completedTorrents.map((torrent) => (
                  <div key={torrent.id} className="flex items-center gap-2 p-2 bg-slate-700/30 rounded-lg hover:bg-slate-700/50 transition-colors">
                    <div className="w-2 h-2 bg-emerald-500 rounded-full flex-shrink-0"></div>
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

      <style jsx>{`
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