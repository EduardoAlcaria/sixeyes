import hmac
import os

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse

from app.routes.torrent_routes import router as torrent_router
from app.routes.system_routes import router as system_router
from app.routes.insight_routes import router as insight_router

AGENT_KEY_HEADER = "x-sixeyes-agent-key"


def _require_agent_key() -> str:
    """The agent exposes filesystem browse, mkdir and download control. In Docker it
    was protected by network isolation; on loopback every local process and user
    account on the machine can reach it. Refuse to start without a shared secret
    rather than silently running open - start.ps1 generates one."""
    key = os.getenv("AGENT_KEY", "")
    if not key:
        raise RuntimeError(
            "AGENT_KEY is not set. The agent will not start unauthenticated."
        )
    return key


def create_app() -> FastAPI:
    app = FastAPI(
        title="SixEyes Python Engine",
        description="Torrent download engine powering the SixEyes middleware.",
        version="1.0.0",
    )

    expected_key = _require_agent_key()

    @app.middleware("http")
    async def verify_agent_key(request: Request, call_next):
        presented = request.headers.get(AGENT_KEY_HEADER, "")
        if not hmac.compare_digest(presented, expected_key):
            return JSONResponse({"detail": "unauthorized"}, status_code=401)
        return await call_next(request)

    # ponytail: no CORS middleware - the agent binds to loopback and only the Java
    # controller calls it. No browser ever reaches this origin.
    app.include_router(torrent_router)
    app.include_router(system_router)
    app.include_router(insight_router)

    return app
