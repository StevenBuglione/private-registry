CREATE TABLE registry_homepage_settings (
    id smallint PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    notification_enabled boolean NOT NULL DEFAULT true,
    notification_title varchar(120) NOT NULL,
    notification_message varchar(600) NOT NULL,
    notification_link_label varchar(80),
    notification_link_url varchar(500),
    featured_provider_ids text NOT NULL,
    updated_by text NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now()
);

INSERT INTO registry_homepage_settings (
    id,
    notification_enabled,
    notification_title,
    notification_message,
    notification_link_label,
    notification_link_url,
    featured_provider_ids,
    updated_by)
VALUES (
    1,
    true,
    'Your private Registry is ready',
    'Browse approved providers and modules from every APM group you belong to.',
    NULL,
    NULL,
    'provider/hashicorp/google,provider/hashicorp/azurerm,provider/hashicorp/aws,provider/hashicorp/kubernetes,provider/hashicorp/helm,provider/hashicorp/datadog',
    'system');

COMMENT ON TABLE registry_homepage_settings IS
    'Singleton administrator-managed presentation settings for the Registry homepage.';
