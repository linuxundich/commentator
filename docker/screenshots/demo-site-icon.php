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
