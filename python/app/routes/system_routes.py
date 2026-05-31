from fastapi import APIRouter, HTTPException
from pydantic import BaseModel

from app.services import torrent_service

router = APIRouter(prefix="/python/systemInfo", tags=["system"])


class MkdirRequest(BaseModel):
    parent: str
    name: str


@router.get("/getStorageInfo")
def get_storage():
    try:
        return torrent_service.get_storage_info()
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/getDisks")
def get_disks():
    try:
        return torrent_service.get_disks()
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/browse")
def browse(path: str | None = None):
    try:
        return torrent_service.list_dir(path)
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/mkdir")
def mkdir(body: MkdirRequest):
    try:
        return torrent_service.make_dir(body.parent, body.name)
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
