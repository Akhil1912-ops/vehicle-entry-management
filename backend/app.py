from contextlib import asynccontextmanager

from fastapi import FastAPI, Depends, HTTPException
from fastapi.staticfiles import StaticFiles
from fastapi.responses import HTMLResponse, FileResponse
from sqlalchemy.orm import Session
from datetime import datetime
from typing import Optional, List
from pydantic import BaseModel

from backend.database import get_db, init_db, Vehicle, EntryLog
from backend.timezone_utils import get_ist_now
from backend.utils import (
    check_suspicious_duration,
    check_suspicious_frequency,
    get_past_entries,
    format_duration,
)


@asynccontextmanager
async def lifespan(app: FastAPI):
    init_db()
    yield


app = FastAPI(title="Vehicle Entry Management System", lifespan=lifespan)

# Pydantic models for request/response
class EntryCheckRequest(BaseModel):
    plate_number: str

class ExitCheckRequest(BaseModel):
    plate_number: str

class VehicleCreate(BaseModel):
    plate_number: str
    owner_name: str
    vehicle_type: Optional[str] = "Unknown"

class EntryResponse(BaseModel):
    is_registered: bool
    plate_number: str
    past_entries: List[dict]
    is_suspicious: bool
    message: str
    suspicious_reason: Optional[str] = ""

class ExitResponse(BaseModel):
    plate_number: str
    entry_time: str
    exit_time: str
    duration_minutes: float
    duration_formatted: str
    is_suspicious: bool
    message: str

# API Endpoints

@app.post("/api/check-entry", response_model=EntryResponse)
async def check_entry(request: EntryCheckRequest, db: Session = Depends(get_db)):
    """Check vehicle at entry gate"""
    plate_number = request.plate_number.upper().strip()
    
    # Check if registered
    vehicle = db.query(Vehicle).filter(Vehicle.plate_number == plate_number).first()
    is_registered = vehicle is not None
    
    # Check for suspicious frequency (unregistered, multiple entries)
    is_suspicious_freq, suspicious_reason = check_suspicious_frequency(db, plate_number, is_registered)
    
    # Get past 3 entries
    past_entries = get_past_entries(db, plate_number, limit=3)
    
    # Create entry log
    entry_log = EntryLog(
        plate_number=plate_number,
        entry_time=get_ist_now(),
        is_registered=is_registered,
        is_suspicious=is_suspicious_freq
    )
    db.add(entry_log)
    db.commit()
    
    # Build message with specific reason
    if is_suspicious_freq:
        message = f"⚠️ RED FLAG: {suspicious_reason}"
    elif not is_registered:
        message = "❌ UNREGISTERED VEHICLE"
    else:
        message = "✅ REGISTERED VEHICLE"
    
    return EntryResponse(
        is_registered=is_registered,
        plate_number=plate_number,
        past_entries=past_entries,
        is_suspicious=is_suspicious_freq,
        message=message,
        suspicious_reason=suspicious_reason if is_suspicious_freq else ""
    )

@app.post("/api/check-exit", response_model=ExitResponse)
async def check_exit(request: ExitCheckRequest, db: Session = Depends(get_db)):
    """Process vehicle exit"""
    plate_number = request.plate_number.upper().strip()
    
    # Find the most recent entry without exit
    entry_log = db.query(EntryLog).filter(
        EntryLog.plate_number == plate_number,
        EntryLog.exit_time.is_(None)
    ).order_by(EntryLog.entry_time.desc()).first()
    
    if not entry_log:
        raise HTTPException(status_code=404, detail="No active entry found for this vehicle")
    
    # Calculate duration
    exit_time = get_ist_now()
    duration = (exit_time - entry_log.entry_time).total_seconds() / 60
    
    # Check if suspicious duration
    is_suspicious_dur = check_suspicious_duration(duration)
    
    # Update entry log
    entry_log.exit_time = exit_time
    entry_log.duration_minutes = duration
    entry_log.is_suspicious = entry_log.is_suspicious or is_suspicious_dur
    db.commit()
    
    # Build message
    if is_suspicious_dur:
        message = f"⚠️ SUSPICIOUS: Stayed {format_duration(duration)} (>20min)"
    else:
        message = "✅ Exit recorded"
    
    return ExitResponse(
        plate_number=plate_number,
        entry_time=entry_log.entry_time.isoformat(),
        exit_time=exit_time.isoformat(),
        duration_minutes=duration,
        duration_formatted=format_duration(duration),
        is_suspicious=is_suspicious_dur,
        message=message
    )

@app.get("/api/history/{plate_number}")
async def get_history(plate_number: str, db: Session = Depends(get_db)):
    """Get full history for a vehicle"""
    plate_number = plate_number.upper().strip()
    entries = db.query(EntryLog).filter(
        EntryLog.plate_number == plate_number
    ).order_by(EntryLog.entry_time.desc()).all()
    
    result = []
    for entry in entries:
        result.append({
            "entry_time": entry.entry_time.isoformat() if entry.entry_time else None,
            "exit_time": entry.exit_time.isoformat() if entry.exit_time else None,
            "duration_minutes": entry.duration_minutes,
            "duration_formatted": format_duration(entry.duration_minutes),
            "is_registered": entry.is_registered,
            "is_suspicious": entry.is_suspicious
        })
    
    return {"plate_number": plate_number, "entries": result}

# Admin endpoints

@app.post("/api/vehicles")
async def create_vehicle(vehicle: VehicleCreate, db: Session = Depends(get_db)):
    """Register a new vehicle"""
    plate_number = vehicle.plate_number.upper().strip()
    
    # Check if already exists
    existing = db.query(Vehicle).filter(Vehicle.plate_number == plate_number).first()
    if existing:
        raise HTTPException(status_code=400, detail="Vehicle already registered")
    
    new_vehicle = Vehicle(
        plate_number=plate_number,
        owner_name=vehicle.owner_name,
        vehicle_type=vehicle.vehicle_type
    )
    db.add(new_vehicle)
    db.commit()
    
    return {"message": "Vehicle registered successfully", "vehicle": {
        "plate_number": new_vehicle.plate_number,
        "owner_name": new_vehicle.owner_name,
        "vehicle_type": new_vehicle.vehicle_type
    }}

@app.get("/api/vehicles")
async def list_vehicles(db: Session = Depends(get_db)):
    """List all registered vehicles"""
    vehicles = db.query(Vehicle).all()
    return [{
        "plate_number": v.plate_number,
        "owner_name": v.owner_name,
        "vehicle_type": v.vehicle_type,
        "registered_date": v.registered_date.isoformat() if v.registered_date else None
    } for v in vehicles]

@app.delete("/api/vehicles/{plate_number}")
async def delete_vehicle(plate_number: str, db: Session = Depends(get_db)):
    """Remove a vehicle from registry"""
    plate_number = plate_number.upper().strip()
    vehicle = db.query(Vehicle).filter(Vehicle.plate_number == plate_number).first()
    
    if not vehicle:
        raise HTTPException(status_code=404, detail="Vehicle not found")
    
    db.delete(vehicle)
    db.commit()
    
    return {"message": "Vehicle removed successfully"}

@app.get("/api/logs")
async def get_all_logs(limit: int = 1000, db: Session = Depends(get_db)):
    """Get all entry logs"""
    logs = db.query(EntryLog).order_by(EntryLog.entry_time.desc()).limit(limit).all()
    
    return [{
        "id": log.id,
        "plate_number": log.plate_number,
        "entry_time": log.entry_time.isoformat() if log.entry_time else None,
        "exit_time": log.exit_time.isoformat() if log.exit_time else None,
        "duration_minutes": log.duration_minutes,
        "duration_formatted": format_duration(log.duration_minutes),
        "is_registered": log.is_registered,
        "is_suspicious": log.is_suspicious
    } for log in logs]

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)

