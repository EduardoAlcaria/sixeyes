from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.routes.torrent_routes import router as torrent_router
from app.routes.system_routes import router as system_router


def create_app() -> FastAPI:
    app = FastAPI(
        title="SixEyes Python Engine",
        description="Torrent download engine powering the SixEyes middleware.",
        version="1.0.0",
    )

    app.add_middleware(
        CORSMiddleware,
        allow_origins=["*"],
        allow_methods=["*"],
        allow_headers=["*"],
    )

    app.include_router(torrent_router)
    app.include_router(system_router)

    return app
