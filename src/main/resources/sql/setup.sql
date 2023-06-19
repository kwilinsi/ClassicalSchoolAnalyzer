CREATE TABLE IF NOT EXISTS Cache
(
    id        INTEGER AUTO_INCREMENT,
    url       VARCHAR(300),
    file_path VARCHAR(200),
    PRIMARY KEY (id),
    UNIQUE (url)
);

CREATE TABLE IF NOT EXISTS Corrections
(
    id                   INTEGER     NOT NULL AUTO_INCREMENT,
    type                 VARCHAR(30) NOT NULL,
    data                 JSON        NOT NULL,
    deserialization_data JSON,
    notes                VARCHAR(300),
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS Organizations
(
    id              INTEGER      NOT NULL AUTO_INCREMENT,
    name            VARCHAR(50)  NOT NULL,
    name_abbr       VARCHAR(10)  NOT NULL,
    homepage_url    VARCHAR(300) NOT NULL,
    school_list_url VARCHAR(300) NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS Districts
(
    id          INTEGER      NOT NULL AUTO_INCREMENT,
    name        VARCHAR(100) NOT NULL,
    website_url VARCHAR(300),
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS DistrictOrganizations
(
    id              INTEGER NOT NULL AUTO_INCREMENT,
    organization_id INTEGER NOT NULL,
    district_id     INTEGER NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (organization_id) REFERENCES Organizations (id),
    FOREIGN KEY (district_id) REFERENCES Districts (id),
    UNIQUE (organization_id, district_id)
);

CREATE TABLE IF NOT EXISTS Schools
(
    id                                      INTEGER      NOT NULL AUTO_INCREMENT,
    district_id                             INTEGER      NOT NULL,
    name                                    VARCHAR(100) NOT NULL,
    phone                                   VARCHAR(20),
    address                                 VARCHAR(100),
    mailing_address                         VARCHAR(100),
    city                                    VARCHAR(50),
    state                                   VARCHAR(40),
    country                                 VARCHAR(30),
    website_url                             VARCHAR(300),
    website_url_redirect                    VARCHAR(300),
    contact_name                            VARCHAR(100),
    email                                   VARCHAR(100),
    accs_accredited                         BOOL,
    office_phone                            VARCHAR(20),
    fax_number                              VARCHAR(20),
    date_accredited                         DATE,
    year_founded                            INTEGER,
    grades_offered                          VARCHAR(100),
    membership_date                         DATE,
    enrollment                              INTEGER,
    number_of_students_k_6                  INTEGER,
    number_of_students_k_6_non_traditional  INTEGER,
    classroom_format                        VARCHAR(100),
    number_of_students_7_12                 INTEGER,
    number_of_students_7_12_non_traditional INTEGER,
    number_of_teachers                      INTEGER,
    student_teacher_ratio                   VARCHAR(50),
    international_student_program           BOOL,
    tuition_range                           VARCHAR(50),
    headmaster_name                         VARCHAR(100),
    church_affiliated                       BOOL,
    chairman_name                           VARCHAR(100),
    accredited_other                        VARCHAR(300),
    latitude                                FLOAT(12, 8),
    longitude                               FLOAT(12, 8),
    lat_long_accuracy                       VARCHAR(25),
    projected_opening                       VARCHAR(20),
    bio                                     TEXT,
    accs_page_url                           VARCHAR(300),
    hillsdale_affiliation_level             VARCHAR(50),
    icle_affiliation_level                  VARCHAR(25),
    icle_page_url                           VARCHAR(300),
    is_excluded                             BOOL         NOT NULL,
    excluded_reason                         VARCHAR(100),
    PRIMARY KEY (id),
    FOREIGN KEY (district_id) REFERENCES Districts (id)
);

CREATE TABLE IF NOT EXISTS Pages
(
    id              INTEGER      NOT NULL AUTO_INCREMENT,
    school_id       INTEGER      NOT NULL,
    url             VARCHAR(300) NOT NULL,
    is_homepage     BOOL         NOT NULL,
    relevancy_score INTEGER,
    PRIMARY KEY (id),
    FOREIGN KEY (school_id) REFERENCES Schools (id),
    UNIQUE (url)
);

CREATE TABLE IF NOT EXISTS Links
(
    id                  INTEGER      NOT NULL AUTO_INCREMENT,
    school_id           INTEGER      NOT NULL,
    source_page_id      INTEGER      NOT NULL,
    target_text         VARCHAR(100),
    target_url          VARCHAR(300) NOT NULL,
    target_url_redirect VARCHAR(300),
    target_page_id      INTEGER,
    is_inner            BOOL         NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (school_id) REFERENCES Schools (id),
    FOREIGN KEY (source_page_id) REFERENCES Pages (id),
    FOREIGN KEY (target_page_id) REFERENCES Pages (id),
    UNIQUE (source_page_id, target_text, target_url)
);

CREATE TABLE IF NOT EXISTS PageTexts
(
    id         INTEGER NOT NULL AUTO_INCREMENT,
    school_id  INTEGER NOT NULL,
    page_id    INTEGER NOT NULL,
    text_group TEXT    NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (school_id) REFERENCES Schools (id),
    FOREIGN KEY (page_id) REFERENCES Pages (id)
);

CREATE TABLE IF NOT EXISTS PageWords
(
    school_id INTEGER     NOT NULL,
    page_id   INTEGER     NOT NULL,
    word      VARCHAR(50) NOT NULL,
    frequency INTEGER     NOT NULL,
    PRIMARY KEY (page_id, word),
    FOREIGN KEY (school_id) REFERENCES Schools (id),
    FOREIGN KEY (page_id) REFERENCES Pages (id)
);
