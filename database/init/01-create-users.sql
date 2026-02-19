








DO
$$
BEGIN
   IF NOT EXISTS (SELECT FROM pg_user WHERE usename = 'bankuser') THEN
      CREATE USER bankuser WITH PASSWORD 'bankpass';
      RAISE NOTICE 'User bankuser created successfully';
   ELSE
      RAISE NOTICE 'User bankuser already exists';
   END IF;
END
$$;
