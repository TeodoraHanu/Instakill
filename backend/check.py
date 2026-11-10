
import argparse
import random
import sqlite3
import uuid
from datetime import datetime, time, timedelta

import bcrypt
from faker import Faker

fake = Faker()

SQL_USER = ("INSERT INTO users (id, username, email, password_hash, created_at) "
            "VALUES (?, ?, ?, ?, ?)")
SQL_POST = ("INSERT INTO posts (id, author_id, content, image_url, created_at, updated_at, "
            "like_count, comment_count) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")
SQL_LIKE = "INSERT INTO likes (id, post_id, user_id, created_at) VALUES (?, ?, ?, ?)"
SQL_COMMENT = ("INSERT INTO comments (id, post_id, author_id, content, created_at) "
               "VALUES (?, ?, ?, ?, ?)")

SAMPLE_VIDEOS = [
    "https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4",
    "https://www.w3schools.com/html/mov_bbb.mp4",
    "https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/360/Big_Buck_Bunny_360_10s_1MB.mp4",
    "https://test-videos.co.uk/vids/jellyfish/mp4/h264/360/Jellyfish_360_10s_1MB.mp4",
]




def generate_password_hash(password: str) -> str:
    return bcrypt.hashpw(password.encode("utf-8"), bcrypt.gensalt()).decode("utf-8")


def to_ms(dt: datetime) -> int:
    """datetime -> milisecunde epoch (formatul folosit de backend)."""
    return int(dt.timestamp() * 1000)


def random_datetime_in_range(start: datetime, end: datetime) -> datetime:
    """Data aleasa cu faker.date_between_dates(), plus o ora aleatoare, in [start, end]."""
    d = fake.date_between_dates(date_start=start.date(), date_end=end.date())
    dt = datetime.combine(d, time.min) + timedelta(seconds=random.randint(0, 86399))
    return max(start, min(dt, end))


def flush(cursor, sql, rows):
    if rows:
        cursor.executemany(sql, rows)
        rows.clear()


def populate(db_path: str, num_users: int, batch_size: int):
    conn = sqlite3.connect(db_path)
    conn.isolation_level = None  # controlam manual tranzactia
    cursor = conn.cursor()
    cursor.execute("PRAGMA foreign_keys = OFF;")
    cursor.execute("BEGIN;")

    try:
        # ---------- Cleanup ----------
        print(">>> Cleanup (pastrez alice si bob)...")
        cursor.execute("DELETE FROM comments;")
        cursor.execute("DELETE FROM likes;")
        cursor.execute("DELETE FROM posts;")
        cursor.execute("DELETE FROM users WHERE username NOT IN ('alice', 'bob');")

        now = datetime.now()
        start_date = now - timedelta(days=7)
        now_ms = to_ms(now)

        used_names = {r[0] for r in cursor.execute("SELECT username FROM users")}
        used_emails = {r[0] for r in cursor.execute("SELECT email FROM users")}
        all_user_ids = [r[0] for r in cursor.execute("SELECT id FROM users")]

        # ---------- charlie ----------
        charlie_id = str(uuid.uuid4())
        cursor.execute(SQL_USER, (charlie_id, "charlie", "charlie@example.com",
                                  generate_password_hash("password"), to_ms(now)))
        used_names.add("charlie")
        used_emails.add("charlie@example.com")
        all_user_ids.append(charlie_id)

        # ---------- Users ----------
        print(f">>> Creating {num_users} users...")
        # Un singur hash bcrypt reutilizat (hash per user ar dura minute)
        default_pass = generate_password_hash("password123")
        users_batch = []
        for _ in range(num_users):
            username = fake.user_name()
            while username in used_names:
                username = f"{fake.user_name()}{random.randint(1, 99999)}"
            used_names.add(username)

            email = f"{username}@example.com"
            while email in used_emails:
                email = f"{username}{random.randint(1, 99999)}@example.com"
            used_emails.add(email)

            u_id = str(uuid.uuid4())
            all_user_ids.append(u_id)
            created = to_ms(random_datetime_in_range(start_date,