from fastapi import FastAPI

from app.routes.torrent_routes import router as torrent_router
from app.routes.system_routes import router as system_router


def create_app() -> FastAPI:
    # No CORS middleware: this API is called only by the Java middleware,
    # server-to-server over the internal docker network, never by a browser.
    # CORS is a browser-enforced policy, so it has no effect here — and a
    # wildcard config is one less permissive surface to carry around.
    app = FastAPI(
        title="SixEyes Python Engine",
        description="Torrent download engine powering the SixEyes middleware.",
        version="1.0.0",
    )

    app.include_router(torrent_router)
    app.include_router(system_router)

    return app
