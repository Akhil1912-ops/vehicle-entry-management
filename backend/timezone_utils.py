"""
Timezone utilities for Indian Standard Time (IST)
IST is UTC+5:30

Note: SQLite stores datetimes as naive (no timezone). We store all times in IST
as naive datetimes, assuming they are always in IST timezone.
"""

from datetime import datetime
import pytz

# Indian Standard Time zone
IST = pytz.timezone('Asia/Kolkata')

def get_ist_now():
    """
    Get current time in Indian Standard Time as a naive datetime.
    SQLite doesn't support timezone-aware datetimes, so we return naive datetime
    but it represents IST time.
    """
    ist_now = datetime.now(IST)
    # Return as naive datetime (SQLite compatible) but represents IST
    return ist_now.replace(tzinfo=None)

def get_ist_datetime(dt=None):
    """
    Convert datetime to IST. If dt is None, returns current IST time.
    Returns naive datetime for SQLite compatibility.
    """
    if dt is None:
        return get_ist_now()
    
    # If datetime is naive, assume it's already in IST (for existing data)
    # For new data, we'll use get_ist_now() which ensures IST
    if dt.tzinfo is None:
        return dt
    
    # If timezone-aware, convert to IST and return as naive
    ist_dt = dt.astimezone(IST)
    return ist_dt.replace(tzinfo=None)

def format_ist_datetime(dt, format_str="%Y-%m-%d %H:%M:%S"):
    """
    Format datetime. Assumes dt is a naive datetime representing IST time.
    """
    if dt is None:
        return None
    
    # dt is naive but represents IST, so format directly
    return dt.strftime(format_str)

