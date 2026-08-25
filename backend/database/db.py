import psycopg2
import os

from dotenv import load_dotenv
from psycopg2.extras import RealDictCursor

load_dotenv()


import time

def get_connection(retries=3, delay=0.5):
    db_url = os.getenv("DATABASE_URL")
    if db_url and "sslmode=" not in db_url:
        sep = "&" if "?" in db_url else "?"
        db_url += f"{sep}sslmode=require"

    for attempt in range(retries):
        try:
            return psycopg2.connect(
                db_url,
                cursor_factory=RealDictCursor,
                connect_timeout=10
            )
        except psycopg2.OperationalError as e:
            if attempt == retries - 1:
                raise e
            time.sleep(delay)