CREATE TABLE IF NOT EXISTS Cache
(
    id        INTEGER AUTO_INCREMENT,
    url       VARCHAR(150),
    file_path VARCHAR(200),
    PRIMARY KEY (id),
    UNIQUE (url)
);

CREATE TABLE IF NOT EXISTS Organizations
(
    id              INT          NOT NULL AUTO_INCREMENT,
    name            VARCHAR(50)  NOT NULL,
    name_abbr       VARCHAR(10)  NOT NULL,
    homepage_url    VARCHAR(100) NOT NULL,
    school_list_url VARCHAR(100) NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS Schools
(
    id                                      INT          NOT NULL AUTO_INCREMENT,
    name                                    VARCHAR(100) NOT NULL,
    organization_id                         INT          NOT NULL,
    phone                                   VARCHAR(20),
    address                                 VARCHAR(100),
    state                                   VARCHAR(40),
    country                                 VARCHAR(30),
    website_url                             VARCHAR(150),
    website_url_redirect                    VARCHAR(150),
    has_website                             BOOL         NOT NULL,
    contact_name                            VARCHAR(100),
    accs_accredited                         BOOL,
    office_phone                            VARCHAR(20),
    date_accredited                         DATE,
    year_founded                            INT,
    grades_offered                          VARCHAR(100),
    membership_date                         DATE,
    number_of_students_k_6                  INT,
    number_of_students_k_6_non_traditional  INT,
    classroom_format                        VARCHAR(100),
    number_of_students_7_12                 INT,
    number_of_students_7_12_non_traditional INT,
    number_of_teachers                      INT,
    student_teacher_ratio                   VARCHAR(50),
    international_student_program           BOOL,
    tuition_range                           VARCHAR(50),
    headmaster_name                         VARCHAR(100),
    church_affiliated                       BOOL,
    chairman_name                           VARCHAR(100),
    accredited_other                        VARCHAR(300),
    accs_page_url                           VARCHAR(150),
    is_excluded                             BOOL         NOT NULL,
    excluded_reason                         VARCHAR(100),
    PRIMARY KEY (id),
    FOREIGN KEY (organization_id) REFERENCES Organizations (id)
);

CREATE TABLE IF NOT EXISTS Pages
(
    id              INT          NOT NULL AUTO_INCREMENT,
    school_id       INT          NOT NULL,
    url             VARCHAR(200) NOT NULL,
    is_homepage     BOOL         NOT NULL,
    relevancy_score INT,
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
