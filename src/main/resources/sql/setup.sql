CREATE TABLE IF NOT EXISTS Organizations
(
    id                    INT          NOT NULL AUTO_INCREMENT,
    name                  VARCHAR(50)  NOT NULL,
    name_abbr             VARCHAR(10)  NOT NULL,
    homepage_url          VARCHAR(100) NOT NULL,
    school_list_url       VARCHAR(100) NOT NULL,
    school_list_page_file VARCHAR(100) DEFAULT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS Schools
(
    id                   INT          NOT NULL AUTO_INCREMENT,
    name                 VARCHAR(100) NOT NULL,
    organization_id      INT          NOT NULL,
    state                VARCHAR(40),
    country              VARCHAR(30)  NOT NULL,
    website_url          VARCHAR(150) NOT NULL,
    website_url_redirect VARCHAR(150),
    has_website          BOOL         NOT NULL,
    is_excluded          BOOL         NOT NULL,
    excluded_reason      VARCHAR(100),
    PRIMARY KEY (id),
    FOREIGN KEY (organization_id) REFERENCES Organizations (id)
);

CREATE TABLE IF NOT EXISTS Pages
(
    id                 INT          NOT NULL AUTO_INCREMENT,
    school_id          INT          NOT NULL,
    url                VARCHAR(200) NOT NULL,
    is_homepage        BOOL         NOT NULL,
    relevancy_score    INT,
    download_file_path VARCHAR(100),
    PRIMARY KEY (id),
    FOREIGN KEY (school_id) REFERENCES Schools (id),
    UNIQUE (url)
);

CREATE TABLE IF NOT EXISTS Links
(
    id                  INT          NOT NULL AUTO_INCREMENT,
    school_id           INT          NOT NULL,
    source_page_id      INT          NOT NULL,
    target_text         VARCHAR(100),
    target_url          VARCHAR(200) NOT NULL,
    target_url_redirect VARCHAR(200),
    target_page_id      INT,
    is_inner            BOOL         NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (school_id) REFERENCES Schools (id),
    FOREIGN KEY (source_page_id) REFERENCES Pages (id),
    FOREIGN KEY (target_page_id) REFERENCES Pages (id),
    UNIQUE (source_page_id, target_text, target_url)
);

CREATE TABLE IF NOT EXISTS PageTexts
(
    id         INT  NOT NULL AUTO_INCREMENT,
    school_id  INT  NOT NULL,
    page_id    INT  NOT NULL,
    text_group TEXT NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (school_id) REFERENCES Schools (id),
    FOREIGN KEY (page_id) REFERENCES Pages (id)
);

CREATE TABLE IF NOT EXISTS PageWords
(
    school_id INT         NOT NULL,
    page_id   INT         NOT NULL,
    word      VARCHAR(50) NOT NULL,
    frequency INT         NOT NULL,
    PRIMARY KEY (page_id, word),
    FOREIGN KEY (school_id) REFERENCES Schools (id),
    FOREIGN KEY (page_id) REFERENCES Pages (id)
);
