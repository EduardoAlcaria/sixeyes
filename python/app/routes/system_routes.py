from fastapi import APIRouter, HTTPException

from app.services import torrent_service

router = APIRouter(prefix="/python/systemInfo", tags=["system"])


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
