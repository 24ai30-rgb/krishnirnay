import os
import tempfile

from fastapi import APIRouter, Depends, File, HTTPException, UploadFile

from app.core.security import verify_api_key
from app.services.disease_ai.disease_model import predict_disease


router = APIRouter(
    prefix="/v1/predict",
    tags=["Disease"],
    dependencies=[Depends(verify_api_key)],
)


@router.post("/disease")
async def disease_prediction(
    image: UploadFile = File(...),
):
    if not image.content_type or not image.content_type.startswith("image/"):
        raise HTTPException(
            status_code=400,
            detail="Please upload a valid image file.",
        )

    image_bytes = await image.read()

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

    except Exception as exc:
        raise HTTPException(
            status_code=500,
            detail=f"Disease prediction failed: {str(exc)}",
        ) from exc

    finally:
        if temp_path and os.path.exists(temp_path):
            os.remove(temp_path)