<?php
/**
 * Plugin Name:       Commentator Bridge
 * Plugin URI:        https://github.com/christophlangner/commentator
 * Description:       Stellt der Android-App Commentator zwei schlanke REST-Endpunkte bereit, damit die regelmäßige Prüfung auf neue Kommentare die Installation nicht belastet.
 * Version:           1.0.0
 * Requires at least: 6.0
 * Requires PHP:      7.4
 * Author:            Christoph Langner
 * License:           MIT
 * License URI:       https://opensource.org/licenses/MIT
 * Text Domain:       commentator-bridge
 *
 * Dieses Plugin ist optional. Ohne es funktioniert die App vollständig, sie
 * stellt dann lediglich etwas teurere Anfragen an die Kern-API.
 *
 * Bewusste Beschränkung: Es werden ausschließlich Leseendpunkte registriert.
 * Das Plugin ändert kein Verhalten von WordPress, hängt sich nicht in die
 * Kommentarverarbeitung ein, schreibt keine Optionen und sendet nichts nach
 * außen.
 */

declare( strict_types = 1 );

if ( ! defined( 'ABSPATH' ) ) {
	exit;
}

const COMMENTATOR_BRIDGE_VERSION   = '1.0.0';
const COMMENTATOR_BRIDGE_NAMESPACE = 'commentator/v1';

add_action( 'rest_api_init', 'commentator_bridge_register_routes' );

function commentator_bridge_register_routes(): void {
	register_rest_route(
		COMMENTATOR_BRIDGE_NAMESPACE,
		'/status',
		array(
			'methods'             => WP_REST_Server::READABLE,
			'callback'            => 'commentator_bridge_status',
			'permission_callback' => 'commentator_bridge_can_moderate',
		)
	);

	register_rest_route(
		COMMENTATOR_BRIDGE_NAMESPACE,
		'/summary',
		array(
			'methods'             => WP_REST_Server::READABLE,
			'callback'            => 'commentator_bridge_summary',
			'permission_callback' => 'commentator_bridge_can_moderate',
		)
	);
}

/**
 * Dieselbe Hürde wie in der Kern-API: Wer keine Kommentare moderieren darf,
 * sieht auch hier nichts.
 */
function commentator_bridge_can_moderate(): bool {
	return current_user_can( 'moderate_comments' );
}

/**
 * Kompakter Zustand für die regelmäßige Prüfung.
 *
 * Der eigentliche Zweck des Plugins: Statt eine vollständige Kommentarliste
 * abzurufen, holt die App drei Werte. Erst wenn sich die neueste Kennung
 * geändert hat, lädt sie tatsächlich Kommentare nach.
 */
function commentator_bridge_status(): WP_REST_Response {
	// wp_count_comments() ist zwischengespeichert und deshalb günstig.
	$counts = wp_count_comments();

	$latest = get_comments(
		array(
			'status'  => 'hold',
			'number'  => 1,
			'orderby' => 'comment_date_gmt',
			'order'   => 'DESC',
			'type'    => 'comment',
		)
	);

	$latest_id   = 0;
	$latest_date = null;

	if ( ! empty( $latest ) ) {
		$comment     = $latest[0];
		$latest_id   = (int) $comment->comment_ID;
		$latest_date = mysql_to_rfc3339( $comment->comment_date_gmt );
	}

	$response = new WP_REST_Response(
		array(
			'pending_count'           => (int) $counts->moderated,
			'latest_comment_id'       => $latest_id,
			'latest_comment_date_gmt' => $latest_date,
			'plugin_version'          => COMMENTATOR_BRIDGE_VERSION,
		)
	);

	// Die Antwort ist naturgemäß flüchtig und darf nicht aus einem Cache kommen.
	$response->header( 'Cache-Control', 'no-store, private' );

	return $response;
}

/**
 * Kommentaranzahl je Status in einem einzigen Aufruf.
 *
 * Ohne diesen Endpunkt bräuchte die Filterleiste der App fünf getrennte
 * Abfragen, nur um Zahlen anzuzeigen.
 */
function commentator_bridge_summary(): WP_REST_Response {
	$counts = wp_count_comments();

	$response = new WP_REST_Response(
		array(
			'counts' => array(
				'approve' => (int) $counts->approved,
				'hold'    => (int) $counts->moderated,
				'spam'    => (int) $counts->spam,
				'trash'   => (int) $counts->trash,
				'all'     => (int) $counts->total_comments,
			),
		)
	);

	$response->header( 'Cache-Control', 'no-store, private' );

	return $response;
}
