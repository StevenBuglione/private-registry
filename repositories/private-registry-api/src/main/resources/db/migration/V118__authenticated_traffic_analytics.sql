CREATE TABLE registry_page_views (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    subject varchar(255) NOT NULL,
    display_name varchar(200) NOT NULL,
    email varchar(320),
    path varchar(512) NOT NULL,
    occurred_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT registry_page_views_path_check
        CHECK (path LIKE '/%' AND path NOT LIKE '%?%' AND path NOT LIKE '%#%')
);

CREATE INDEX registry_page_views_occurred_at_idx
    ON registry_page_views (occurred_at DESC, id DESC);

CREATE INDEX registry_page_views_subject_idx
    ON registry_page_views (subject, occurred_at DESC);

CREATE INDEX registry_page_views_path_idx
    ON registry_page_views (path, occurred_at DESC);

COMMENT ON TABLE registry_page_views IS
    'Privacy-minimized authenticated UI page views retained for administrator traffic reporting.';
