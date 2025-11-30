# SixEyes 🚀
### A stable prototype uniting Cloud, Java (Spring Boot), Python and React in a remote torrent management system

A complete system to manage torrents remotely using modern hybrid architecture. Control your torrent client from anywhere — no VPN, no port forwarding, no headaches.

**Simple idea. Complex engineering.**

---

## 🧱 Hybrid Architecture

SixEyes is built on three complementary pillars that work in perfect harmony:

### ⚙️ **Middleware Layer**
**Spring Boot** running on **Google Cloud Run**
- RESTful API orchestration
- **Processes and transforms Python agent responses**
- Business logic and state management
- **Data formatting and aggregation**
- Auto-scaling serverless deployment
- Health checks and monitoring

### 🎨 **Frontend Layer**
**React** also on **Cloud Run**
- Real-time dashboard with live updates
- Torrent status visualization
- System analytics and charts
- Responsive, modern UI with Tailwind CSS

### 🐍 **Local Agent**
**Python** executing directly on user's machine
- LibTorrent protocol implementation
- Secure tunnel via **ngrok**
- Real-time command execution
- Raw torrent data collection
- Works without fixed IP or port forwarding

**The bridge:** Cloud ↔ Local happens through a secure ngrok tunnel, enabling real-time communication even without static IP. The Spring middleware acts as an intelligent intermediary, processing raw data from Python and serving clean, formatted responses to React.

---

## 🌐 Tech Stack

| Layer | Technology |
|-------|------------|
| **Frontend** | React 18.3, Vite 7.1, Tailwind CSS 3.4, Recharts 2.8 |
| **Backend** | Java 24, Spring Boot 3.5.5, Spring Security, Maven |
| **Agent** | Python 3.9+, Flask, LibTorrent, Flask-CORS |
| **Cloud** | Google Cloud Run, Artifact Registry |
| **DevOps** | GitHub Actions, Docker, JUnit 5 |
| **Protocols** | REST API, HTTP/HTTPS, BitTorrent |
| **Tunnel** | ngrok (secure local-to-cloud communication) |

---

## 💥 GitHub Actions: Automation That Matters

Implemented CI/CD pipelines that:

✅ **Build** Docker images automatically  
✅ **Run** unit and integration tests  
✅ **Analyze** code quality  
✅ **Publish** versions to Cloud Run  

**Push → Build → Deploy.** That simple.

### Backend Pipeline
```
1. Test (JUnit + Mockito)
2. Merge dev → master
3. Maven package + Docker build
4. Push to Artifact Registry
5. Deploy to Cloud Run (auto-scaling 0-10 instances)
6. Health check validation
```

### Frontend Pipeline
```
1. npm build + ESLint
2. Bundle size analysis
3. Docker multi-stage build
4. Deploy to Cloud Run (auto-scaling 0-5 instances)
5. CORS configuration sync
```

---

## ⚡ Real-Time End-to-End Flow

The system works seamlessly with intelligent data processing at each layer:

```
React UI → Spring Backend → Python Agent → LibTorrent
   ↑            ↓                   ↓
   │      [PROCESSES &         [RAW DATA:
   │       TRANSFORMS]          speed, peers,
   │            ↓               progress, etc]
   │      [FORMATTED             ↓
   │       RESPONSE]             ↓
   └────────────────────────────┘
```

**Complete Flow:**
1. **Frontend sends** request (add torrent, pause, get status)
2. **Middleware receives** and validates the request
3. **Middleware forwards** command to Python agent via ngrok
4. **Agent executes** torrent operations using LibTorrent
5. **Agent returns** raw data (speeds, peers, file info)
6. **Middleware processes** the response:
   - Formats speeds (bytes → MB/s)
   - Calculates progress percentages
   - Aggregates system metrics
   - Transforms enums (Python Status → Java TorrentStatus)
   - Adds timestamps and metadata
7. **Middleware sends** clean, formatted JSON to React
8. **React updates** UI with processed data

### Example Data Transformation

**Python Agent Response:**
```json
{
  "downloadSpeed": 5432100,  // bytes/s
  "uploadSpeed": 1234567,
  "progress": 0.7523,
  "status": "downloading"
}
```

**Spring Middleware Processes and Returns:**
```json
{
  "id": 1,
  "downloadSpeed": "5.18 MB/s",
  "uploadSpeed": "1.18 MB/s", 
  "progress": 75.23,
  "status": "Downloading",
  "updatedAt": "2025-01-15T10:30:45"
}
```

All in real-time with 5-second polling intervals for live monitoring.

---

## 🚀 Key Features

### ✅ **Implemented**
- Magnet link support (add torrents instantly)
- Pause/Resume/Remove controls with state persistence
- Real-time speed graphs (download/upload)
- **Intelligent data processing layer** (formatting, aggregation, validation)
- Storage monitoring and analytics
- Completed downloads tracking
- Peer connection statistics
- **System health metrics aggregation**
- Auto-scaling cloud deployment
- Automated CI/CD pipelines
- Secure tunnel communication (ngrok)

### 🔧 **Planned Improvements**
- More robust security (JWT authentication)
- New features (scheduling, priorities)
- Refined interface (themes, customization)
- Cleaner codebase (refactoring)
- Agent optimizations (performance)
- WebSocket integration (replace polling)
- **Enhanced middleware caching** (reduce agent calls)

---

## 🎯 What SixEyes Proves

🔹 **Hybrid architectures are efficient** — Cloud + Local best of both worlds  
🔹 **Python, Java and React form a powerful ecosystem** — Each excels at its role  
🔹 **Middleware processing is crucial** — Clean separation of concerns  
🔹 **Cloud Run + GitHub Actions simplify deployments** — Push to production in minutes  
🔹 **Automation accelerates even prototypes** — CI/CD from day one  
🔹 **You can build remote systems without expensive infrastructure** — Serverless FTW  

---


## 🛠️ Quick Start

### Local Development with Docker Compose
```bash
git clone https://github.com/yourusername/sixeyes
cd sixeyes
docker-compose up
```

Access:
- Frontend: `http://localhost:3000`
- Backend: `http://localhost:9090`
- Python Agent: `http://localhost:9999`

### Manual Setup
```bash
# Backend
cd java/controller
mvn spring-boot:run

# Python Agent
cd python
pip install -r requirements.txt
python downloader.py

# Frontend
cd js/torrent-dashboard
npm install && npm run dev
```

---

## 📊 System Architecture

```
┌─────────────────────────────────────┐
│         React Dashboard             │
│    (Cloud Run - Auto-scaling)       │
│   Real-time UI + Data Viz           │
└─────────────┬───────────────────────┘
              │ REST API (formatted data)
              ▼
┌─────────────────────────────────────┐
│      Spring Boot Middleware         │
│    (Cloud Run - Auto-scaling)       │
│                                     │
│  ✓ Validates requests               │
│  ✓ Forwards to Python agent         │
│  ✓ Receives raw torrent data        │
│  ✓ Processes & formats response     │
│  ✓ Aggregates system metrics        │
│  ✓ Manages state & caching          │
│  ✓ Returns clean JSON to React      │
└─────────────┬───────────────────────┘
              │ HTTP over ngrok tunnel (raw data)
              ▼
┌─────────────────────────────────────┐
│      Python Local Agent             │
│    (User's Machine + ngrok)         │
│   LibTorrent + File System          │
│   Raw data collection               │
└─────────────────────────────────────┘

Region: southamerica-east1 (São Paulo)
```

---

## 🔄 Middleware Responsibilities

The Spring Boot middleware is not just a proxy — it's an intelligent processing layer:

### 📥 **Inbound Processing**
- Request validation and sanitization
- Authentication and authorization (planned)
- Rate limiting and throttling (planned)

### 🔄 **Agent Communication**
- Forwards commands to Python agent
- Handles timeout and retry logic
- Manages ngrok tunnel connectivity

### 📤 **Outbound Processing**
- Transforms raw LibTorrent data
- Formats speeds (bytes/s → MB/s with 2 decimals)
- Calculates percentages and ETA
- Aggregates multiple torrent statistics
- Adds timestamps and metadata
- Maps Python enums to Java entities

### 💾 **State Management**
- Maintains torrent registry (in-memory H2)
- Tracks torrent lifecycle
- Provides RESTful interface to React

---

## 🔐 Security Features

- Spring Security with CSRF protection
- CORS configuration for Cloud Run domains
- Secure ngrok tunnel (HTTPS)
- Environment variable injection
- No credentials in codebase (GitHub Secrets)
- Input validation at middleware layer

---

## 💬 Want a Custom Version?

Interested in adapting this architecture for other types of remote automation? 

**Let's talk!** Comment or reach out directly.

---

## 📬 Contact

**Made with 🔥 by Eduardo Alcaria**

💼 [LinkedIn](https://linkedin.com/in/eduardoalcaria)  
📧 eduardoalcarialopes@gmail.com  
🐙 [GitHub](https://github.com/eduardoalcaria) — Full code available  

---

**License:** MIT  
**Status:** ![Stable Prototype](https://img.shields.io/badge/status-stable%20prototype-green) ![CI/CD](https://img.shields.io/badge/CI%2FCD-automated-blue) ![Cloud](https://img.shields.io/badge/cloud-GCP-blue)
