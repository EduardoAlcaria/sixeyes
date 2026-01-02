from fastapi import APIRouter, HTTPException, UploadFile, File
from pydantic import BaseModel

from app.services import torrent_service

router = APIRouter(prefix="/python", tags=["torrents"])


class AddTorrentRequest(BaseModel):
    id: int
    magnet: str
    downloadPath: str | None = None


class TorrentIdRequest(BaseModel):
    id: int
    magnet: str | None = None


@router.post("/add", status_code=201)
def add(body: AddTorrentRequest):
    return torrent_service.add_torrent(body.id, body.magnet, body.downloadPath)


@router.get("/get")
def get_all():
    return torrent_service.get_all()


@router.put("/pause")
def pause(body: TorrentIdRequest):
    try:
        torrent_service.pause(body.id)
        return {"success": True}
    except KeyError as e:
        raise HTTPException(status_code=404, detail=str(e))


@router.put("/resume")
def resume(body: TorrentIdRequest):
    try:
        torrent_service.resume(body.id)
        return {"success": True}
    except KeyError as e:
        raise HTTPException(status_code=404, detail=str(e))


@router.delete("/remove")
def remove(body: TorrentIdRequest):
    torrent_service.remove(body.id)
    return {"success": True, "message": "Torrent removed"}


@router.post("/parseMagnet")
async def parse_magnet(file: UploadFile = File(...)):
    raw = await file.read()
    try:
        return {"magnet": torrent_service.magnet_from_torrent_bytes(raw)}
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"Invalid .torrent file: {e}")


@router.get("/test")
def health():
    return {"status": "UP", "service": "python-engine"}
