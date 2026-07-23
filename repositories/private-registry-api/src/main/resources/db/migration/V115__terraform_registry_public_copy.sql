UPDATE registry_homepage_settings
   SET notification_message = 'Browse Terraform providers and modules available to your account.',
       updated_at = now()
 WHERE notification_message =
       'Browse approved providers and modules from every APM group you belong to.';
