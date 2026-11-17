import argparse
import random
import psycopg2
from psycopg2.extras import execute_batch
import uuid
from datetime import datetime, time, timedelta, timezone

import bcrypt
from faker import Faker

fake = Faker()

SQL_USER = ("INSERT INTO users (id, username, email, password_hash, created_at) "
            "VALUES (%s, %s, %s, %s, %s)")
SQL_POST = ("INSERT INTO posts (id, author_id, content, image_url, created_at, updated_at, "
            "like_count, comment_count) VALUES (%s, %s, %s, %s, %s, %s, %s, %s)")
SQL_LIKE = "INSERT INTO likes (id, post_id, user_id, created_at) VALUES (%s, %s, %s, %s)"
SQL_COMMENT = ("INSERT INTO comments (id, post_id, author_id, content, created_at) "
               "VALUES (%s, %s, %s, %s, %s)")

SAMPLE_VIDEOS = [
    "https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4",
    "https://www.w3schools.com/html/mov_bbb.mp4",
    "https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/360/Big_Buck_Bunny_360_10s_1MB.mp4",
    "https://test-videos.co.uk/vids/jellyfish/mp4/h264/360/Jellyfish_360_10s_1MB.mp4",
]

INTEGRITY_CHECKS = {
    "posts fara autor": (
        "SELECT COUNT(*) FROM posts p "
        "LEFT JOIN users u ON u.id = p.author_id WHERE u.id IS NULL"),
    "likes fara post": (
        "SELECT COUNT(*) FROM likes l "
        "LEFT JOIN posts p ON p.id = l.post_id WHERE p.id IS NULL"),
    "likes fara user": (
        "SELECT COUNT(*) FROM likes l "
        "LEFT JOIN users u ON u.id = l.user_id WHERE u.id IS NULL"),
    "comments fara post": (
        "SELECT COUNT(*) FROM comments c "
        "LEFT JOIN posts p ON p.id = c.post_id WHERE p.id IS NULL"),
    "comments fara autor": (
        "SELECT COUNT(*) FROM comments c "
        "LEFT JOIN users u ON u.id = c.author_id WHERE u.id IS NULL"),
    "like_count gresit": (
        "SELECT COUNT(*) FROM posts p "
        "LEFT JOIN (SELECT post_id, COUNT(*) AS n FROM likes GROUP BY post_id) l "
        "ON l.post_id = p.id "
        "WHERE p.like_count != COALESCE(l.n, 0)"),
    "comment_count gresit": (
        "SELECT COUNT(*) FROM posts p "
        "LEFT JOIN (SELECT post_id, COUNT(*) AS n FROM comments GROUP BY post_id) c "
        "ON c.post_id = p.id "
        "WHERE p.comment_count != COALESCE(c.n, 0)"),
    "like-uri duplicate": (
        "SELECT COUNT(*) FROM (SELECT 1 FROM likes "
        "GROUP BY post_id, user_id HAVING COUNT(*) > 1) AS dup"),
}


def generate_password_hash(password: str) -> str:
    return bcrypt.hashpw(password.encode("utf-8"), bcrypt.gensalt()).decode("utf-8")


def random_datetime_in_range(start: datetime, end: datetime) -> datetime:
    """Data aleasa cu faker.date_between_dates(), plus o ora aleatoare, in [start, end]."""
    d = fake.date_between_dates(date_start=start.date(), date_end=end.date())
    dt = datetime.combine(d, time.min, tzinfo=timezone.utc) + timedelta(seconds=random.randint(0, 86399))
    return max(start, min(dt, end))


def random_between(start: datetime, end: datetime) -> datetime:
    delta_sec = max(0, int((end - start).total_seconds()))
    return start + timedelta(seconds=random.randint(0, delta_sec))


def flush(cursor, sql, rows, batch_size):
    if rows:
        execute_batch(cursor, sql, rows, page_size=batch_size)
        rows.clear()


def populate(db_params: dict, num_users: int, batch_size: int):
    conn = psycopg2.connect(**db_params)
    conn.autocommit = False
    cursor = conn.cursor()

    try:
        # ---------- Cleanup ----------
        print(">>> Cleanup (pastrez alice si bob)...")
        cursor.execute("DELETE FROM comments;")
        cursor.execute("DELETE FROM likes;")
        cursor.execute("DELETE FROM posts;")
        cursor.execute("DELETE FROM users WHERE username NOT IN ('alice', 'bob');")

        now = datetime.now(timezone.utc)
        start_date = now - timedelta(days=7)

        cursor.execute("SELECT username FROM users")
        used_names = {r[0] for r in cursor.fetchall()}
        cursor.execute("SELECT email FROM users")
        used_emails = {r[0] for r in cursor.fetchall()}
        cursor.execute("SELECT id FROM users")
        all_user_ids = [r[0] for r in cursor.fetchall()]

        # ---------- charlie ----------
        charlie_id = str(uuid.uuid4())
        cursor.execute(SQL_USER, (charlie_id, "charlie", "charlie@example.com",
                                  generate_password_hash("password"), now))
        used_names.add("charlie")
        used_emails.add("charlie@example.com")
        all_user_ids.append(charlie_id)

        # ---------- Users ----------
        print(f">>> Creating {num_users} users...")
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
            created = random_datetime_in_range(start_date, now)
            users_batch.append((u_id, username, email, default_pass, created))

            if len(users_batch) >= batch_size:
                flush(cursor, SQL_USER, users_batch, batch_size)
        flush(cursor, SQL_USER, users_batch, batch_size)

        # ---------- Posts, likes, comments ----------
        print(">>> Creating posts, likes & comments...")
        posts_b, likes_b, comments_b = [], [], []
        total_posts = 0

        for user_id in all_user_ids:
            for _ in range(random.randint(10, 20)):
                p_id = str(uuid.uuid4())
                content = " ".join(fake.words(nb=random.randint(1, 10)))

                if random.choice([True, False]):
                    image_url = random.choice(SAMPLE_VIDEOS)
                else:
                    image_url = f"https://picsum.photos/seed/{p_id}/600/400"

                p_ts = random_datetime_in_range(start_date, now)

                # Like-uri: useri distincti per postare
                n_likes = min(random.randint(0, 20), len(all_user_ids))
                likers = random.sample(all_user_ids, n_likes)
                for liker_id in likers:
                    l_ts = random_between(p_ts, now)
                    likes_b.append((str(uuid.uuid4()), p_id, liker_id, l_ts))

                # Comentarii
                n_comments = random.randint(0, 10)
                for _ in range(n_comments):
                    c_ts = random_between(p_ts, now)
                    comments_b.append((
                        str(uuid.uuid4()), p_id, random.choice(all_user_ids),
                        " ".join(fake.words(nb=random.randint(1, 10))), c_ts))

                posts_b.append((p_id, user_id, content, image_url, p_ts, p_ts,
                                n_likes, n_comments))
                total_posts += 1

                if (len(posts_b) >= batch_size or len(likes_b) >= batch_size
                        or len(comments_b) >= batch_size):
                    flush(cursor, SQL_POST, posts_b, batch_size)
                    flush(cursor, SQL_LIKE, likes_b, batch_size)
                    flush(cursor, SQL_COMMENT, comments_b, batch_size)

        flush(cursor, SQL_POST, posts_b, batch_size)
        flush(cursor, SQL_LIKE, likes_b, batch_size)
        flush(cursor, SQL_COMMENT, comments_b, batch_size)

        # ---------- Index ----------
        print(">>> Creez indexul pe likes(post_id)...")
        cursor.execute("CREATE INDEX IF NOT EXISTS idx_likes_post ON likes (post_id);")

        # ---------- Verificari de integritate ----------
        print(">>> Verific integritatea datelor...")
        for name, query in INTEGRITY_CHECKS.items():
            cursor.execute(query)
            n = cursor.fetchone()[0]
            print(f"    {name}: {n}")
            assert n == 0, f"Integritate incalcata: {name} ({n})"

        conn.commit()
        print(f">>> Gata: {len(all_user_ids)} useri, {total_posts} postari.")
    except BaseException:
        try:
            conn.rollback()
        except psycopg2.Error:
            pass
        print(">>> Eroare/intrerupere, ROLLBACK efectuat. Baza de date nu a fost modificata.")
        raise
    finally:
        cursor.close()
        conn.close()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Populate Instakill PostgreSQL Database")
    parser.add_argument("--host", default="localhost", help="PostgreSQL host")
    parser.add_argument("--port", type=int, default=5432, help="PostgreSQL port")
    parser.add_argument("--dbname", default="instakill", help="Numele bazei de date")
    parser.add_argument("--user", default="instakill", help="Userul PostgreSQL")
    parser.add_argument("--password", default="instakill_password", help="Parola PostgreSQL")
    parser.add_argument("--users", type=int, default=10000, help="Numar de useri generati")
    parser.add_argument("--batch-size", type=int, default=5000, help="Randuri per batch")
    parser.add_argument("--seed", type=int, default=None, help="Seed pentru date reproductibile")
    args = parser.parse_args()

    if args.seed is not None:
        random.seed(args.seed)
        Faker.seed(args.seed)

    db_params = {
        "host": args.host,
        "port": args.port,
        "dbname": args.dbname,
        "user": args.user,
        "password": args.password,
    }

    populate(db_params, args.users, args.batch_size)