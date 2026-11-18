from fastapi import APIRouter, HTTPException
from pydantic import BaseModel

from app.services import games, hostinfo, steam, torrent_service

router = APIRouter(prefix="/python", tags=["insight"])


class RootsRequest(BaseModel):
    roots: list[str] = []


class ExeRequest(BaseModel):
    dir: str
    roots: list[str]


class LaunchRequest(BaseModel):
    exe: str
    roots: list[str]


class SteamLaunchRequest(BaseModel):
    appId: str


@router.get("/details/{torrent_id}")
def details(torrent_id: int):
    try:
        return torrent_service.details(torrent_id)
    except KeyError as e:
        raise HTTPException(status_code=404, detail=str(e))


@router.get("/peers")
def peers(id: int | None = None):
    return torrent_service.peer_map(id)


@router.get("/systemInfo/vitals")
def vitals():
    return hostinfo.vitals()


@router.get("/steam/library")
def steam_library(force: bool = False):
    return {"root": steam.steam_root(), "libraries": steam.library_paths(), "games": steam.library(force),
            "owned": steam.owned_not_installed(), "account": steam.account_id()}


@router.post("/steam/install")
def steam_install(body: SteamLaunchRequest):
    try:
        steam.install(body.appId)
        return {"success": True}
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))


@router.post("/steam/launch")
def steam_launch(body: SteamLaunchRequest):
    try:
        steam.launch(body.appId)
        return {"success": True}
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))


@router.get("/games/sizes")
def game_sizes(root: str):
    try:
        return games.sizes(root)
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))


@router.post("/games/exe")
def game_exe(body: ExeRequest):
    try:
        return {"exe": games.find_exe(body.dir, body.roots)}
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))


@router.post("/games/launch")
def game_launch(body: LaunchRequest):
    try:
        return {"pid": games.launch(body.exe, body.roots)}
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))


@router.post("/games/running")
def game_running(body: RootsRequest):
    return games.running(body.roots)
