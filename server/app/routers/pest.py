from fastapi import APIRouter, Depends, File, HTTPException, UploadFile

from PIL import UnidentifiedImageError

from app.core.security import verify_api_key
from app.routers.disease import MAX_UPLOAD_BYTES

router = APIRouter(
    prefix="/v1",
    tags=["pest"],
    dependencies=[Depends(verify_api_key)],
)

# YOLO model
try:
    from app.services.pest_model import predict_pests
except ImportError:
    predict_pests = None


@router.post("/predict/pest")
async def predict_pest(file: UploadFile = File(...)):
    """
    Detect agricultural pests from a plant image using YOLOv8.
    """

    if predict_pests is None:
        raise HTTPException(
            status_code=503,
            detail={
                "error": "model_unavailable",
                "message": "Pest detection model service is not available.",
            },
        )

    if not file.content_type or not file.content_type.startswith("image/"):
        raise HTTPException(
            status_code=400,
            detail={
                "error": "invalid_file",
                "message": "Please upload a valid plant image.",
            },
        )

    try:
        image_bytes = await file.read(MAX_UPLOAD_BYTES + 1)

        if len(image_bytes) > MAX_UPLOAD_BYTES:
            raise HTTPException(
                status_code=413,
                detail={
                    "error": "file_too_large",
                    "message": "Image is too large (max 5 MB).",
                },
            )

        if not image_bytes:
            raise HTTPException(
                status_code=400,
                detail={
                    "error": "empty_file",
                    "message": "Uploaded image is empty.",
                },
            )

        result = predict_pests(image_bytes)

        return {
            "success": True,
            "message": "Pest detection completed.",
            **result,
        }

    except HTTPException:
        raise

    except UnidentifiedImageError:
        raise HTTPException(
            status_code=400,
            detail={
                "error": "invalid_file",
                "message": "Could not read the image. Please upload a JPEG or PNG photo.",
            },
        )

    except Exception as exc:
        print("PEST DETECTION ERROR:", repr(exc))

        raise HTTPException(
            status_code=500,
            detail={
                "error": "prediction_failed",
                "message": "Pest detection failed.",
            },
        )