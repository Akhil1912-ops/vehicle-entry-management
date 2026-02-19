from sqlalchemy import create_engine, Column, Integer, String, DateTime, Boolean, Float
from sqlalchemy.orm import sessionmaker, DeclarativeBase

from backend.timezone_utils import get_ist_now


class Base(DeclarativeBase):
    pass

class Vehicle(Base):
    __tablename__ = "vehicles"
    
    plate_number = Column(String, primary_key=True)
    owner_name = Column(String)
    vehicle_type = Column(String)
    registered_date = Column(DateTime, default=get_ist_now)

class EntryLog(Base):
    __tablename__ = "entry_logs"
    
    id = Column(Integer, primary_key=True, autoincrement=True)
    plate_number = Column(String)
    entry_time = Column(DateTime)
    exit_time = Column(DateTime, nullable=True)
    duration_minutes = Column(Float, nullable=True)
    is_registered = Column(Boolean)
    is_suspicious = Column(Boolean, default=False)
    created_at = Column(DateTime, default=get_ist_now)

# SQLite database
DATABASE_URL = "sqlite:///./vehicle_tracking.db"
engine = create_engine(DATABASE_URL, connect_args={"check_same_thread": False})
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)

def init_db():
    """Initialize database tables"""
    Base.metadata.create_all(bind=engine)

def get_db():
    """Get database session"""
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()

