CREATE TABLE categories
(
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(500)
);

CREATE TABLE plants
(
    id                BIGSERIAL PRIMARY KEY,
    name              VARCHAR(150)   NOT NULL,
    latin_name        VARCHAR(150),
    description       VARCHAR(2000),
    price             NUMERIC(10, 2) NOT NULL CHECK (price >= 0),
    category_id       BIGINT         NOT NULL REFERENCES categories (id),
    care_level        VARCHAR(20)    NOT NULL,
    light_requirement VARCHAR(30)    NOT NULL,
    pot_size_cm       INTEGER CHECK (pot_size_cm > 0),
    image_url         VARCHAR(500),
    active            BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_plants_category_id ON plants (category_id);
CREATE INDEX idx_plants_care_level ON plants (care_level);
