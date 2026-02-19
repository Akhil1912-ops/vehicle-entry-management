from datetime import datetime, timedelta
from sqlalchemy.orm import Session
from backend.database import EntryLog
from backend.timezone_utils import get_ist_now

def check_suspicious_duration(duration_minutes: float) -> bool:
    """Check if duration is suspicious (>20 minutes)"""
    return duration_minutes > 20 if duration_minutes else False

def check_suspicious_frequency(db: Session, plate_number: str, is_registered: bool):
    """
    Check if vehicle entered suspiciously frequently.
    Returns: (is_suspicious: bool, reason: str)
    """
    if is_registered:
        return False, ""
    
    now = get_ist_now()
    twenty_minutes_ago = now - timedelta(minutes=20)
    one_hour_ago = now - timedelta(hours=1)
    
    # Count entries in last 20 minutes (before adding current entry)
    entries_20min = db.query(EntryLog).filter(
        EntryLog.plate_number == plate_number,
        EntryLog.entry_time >= twenty_minutes_ago
    ).count()
    
    # Count entries in last 1 hour (before adding current entry)
    entries_1hr = db.query(EntryLog).filter(
        EntryLog.plate_number == plate_number,
        EntryLog.entry_time >= one_hour_ago
    ).count()
    
    # Check conditions
    # If there's already 1+ entry in last 20 min, this new entry makes it 2+, so flag it
    if entries_20min >= 1:
        return True, "Entered more than 1 time in last 20 minutes"
    
    # If there's already 1+ entry in last 1 hour, this new entry makes it 2+, so flag it
    if entries_1hr >= 1:
        return True, "Entered 2+ times in last 1 hour"
    
    return False, ""

def get_past_entries(db: Session, plate_number: str, limit: int = 3):
    """Get past N entries for a vehicle"""
    entries = db.query(EntryLog).filter(
        EntryLog.plate_number == plate_number
    ).order_by(EntryLog.entry_time.desc()).limit(limit).all()
    
    result = []
    now = get_ist_now()
    
    for entry in entries:
        # Calculate time ago
        if entry.exit_time:
            time_ago = now - entry.exit_time
        else:
            time_ago = now - entry.entry_time
        
        hours = int(time_ago.total_seconds() // 3600)
        minutes = int((time_ago.total_seconds() % 3600) // 60)
        
        if hours > 0:
            time_ago_str = f"{hours}hr {minutes}min ago"
        else:
            time_ago_str = f"{minutes}min ago"
        
        result.append({
            "entry_time": entry.entry_time.isoformat() if entry.entry_time else None,
            "exit_time": entry.exit_time.isoformat() if entry.exit_time else None,
            "duration_minutes": entry.duration_minutes,
            "time_ago": time_ago_str,
            "is_suspicious": entry.is_suspicious
        })
    
    return result

def format_duration(minutes: float) -> str:
    """Format duration in minutes to readable string"""
    if not minutes:
        return "N/A"
    
    hours = int(minutes // 60)
    mins = int(minutes % 60)
    
    if hours > 0:
        return f"{hours}hr {mins}min"
    return f"{mins}min"

