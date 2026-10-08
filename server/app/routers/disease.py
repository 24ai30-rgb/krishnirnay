import os
import tempfile

from fastapi import APIRouter, Depends, File, HTTPException, UploadFile
from PIL import UnidentifiedImageError

from app.core.security import verify_api_key
from app.services.disease_ai.disease_model import predict_disease


router = APIRouter(
    prefix="/v1/predict",
    tags=["Disease"],
    dependencies=[Depends(verify_api_key)],
)

MAX_UPLOAD_BYTES = 5 * 1024 * 1024


@router.post("/disease")
async def disease_prediction(
    image: UploadFile = File(...),
):
    if not image.content_type or not image.content_type.startswith("image/"):
        raise HTTPException(
            status_code=415,
            detail="Please upload a valid image file.",
        )

    image_bytes = await image.read(MAX_UPLOAD_BYTES + 1)

    if len(image_bytes) > MAX_UPLOAD_BYTES:
        raise HTTPException(
            status_code=413,
            detail="Image is too large (max 5 MB).",
        )

    if not image_bytes:
        raise HTTPException(
            status_code=400,
            detail="Uploaded image is empty.",
        )

    temp_path = None

    try:
        suffix = os.path.splitext(image.filename or "")[1] or ".jpg"

        with tempfile.NamedTemporaryFile(
            delete=False,
            suffix=suffix,
        ) as temp_file:
            temp_file.write(image_bytes)
            temp_path = temp_file.name

        result = predict_disease(temp_path)

        return result

    except UnidentifiedImageError as exc:
        raise HTTPException(
            status_code=400,
            detail="Could not read the image. Please upload a JPEG or PNG photo.",
        ) from exc

    except Exception as exc:
        raise HTTPException(
            status_code=500,
            detail=f"Disease prediction failed: {str(exc)}",
        ) from exc

    finally:
        if temp_path and os.path.exists(temp_path):
            os.remove(temp_path)