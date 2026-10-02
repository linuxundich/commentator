<?php
/**
 * Nur für die Testumgebung: Site-Icon aus der Option commentator_demo_site_icon.
 *
 * Für Screenshots mit dem Logo eines echten Blogs, ohne dessen Datei in die
 * Testumgebung zu kopieren. Wird von scripts/seed-screenshots.sh als
 * Must-Use-Plugin eingespielt.
 */
add_filter(
	'get_site_icon_url',
	static function ( $url ) {
		$demo = get_option( 'commentator_demo_site_icon' );
		return $demo ? $demo : $url;
	}
);

// Ebenfalls nur hier: Kommentare ohne Anmeldung über die REST-API, damit das
// Demo-Video Leserkommentare ohne Verzögerung anlegen kann - so, wie sie über
// das Formular der Website hereinkämen.
add_filter( 'rest_allow_anonymous_comments', '__return_true' );

// Und ohne Flood-Schutz: In der Testumgebung kommt jeder Kommentar von
// derselben Adresse, und WordPress lehnte die Rückfrage im Video sonst als zu
// schnell ab.
add_filter( 'wp_is_comment_flood', '__return_false', PHP_INT_MAX );
