<?php
/**
 * Plugin Name:       Commentator Bridge
 * Plugin URI:        https://github.com/christophlangner/commentator
 * Description:       Stellt der Android-App Commentator schlanke REST-Endpunkte bereit: für die regelmäßige Prüfung auf neue Kommentare sowie für Sammelaktionen, die die Kern-API nicht kennt.
 * Version:           1.4.0
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
 * Bewusste Beschränkung: Das Plugin hängt sich nicht in die
 * Kommentarverarbeitung ein, ändert kein Verhalten von WordPress und sendet
 * nichts nach außen.
 *
 * Seit 1.2.0 gibt es zwei schreibende Endpunkte. Beide tun ausschließlich
 * das, was im Backend ohnehin möglich ist, und prüfen dieselben Rechte:
 *
 * - /empty leert Spam oder Papierkorb, wie der gleichnamige Knopf in der
 *   Kommentarverwaltung. Verlangt `moderate_comments`.
 * - /blocklist pflegt die Option `disallowed_keys`, also dieselbe Liste wie
 *   Einstellungen → Diskussion. Verlangt `manage_options`, weil es eine
 *   seitenweite Option ist; ein Redakteur darf moderieren, aber keine
 *   Optionen ändern.
 */

declare( strict_types = 1 );

if ( ! defined( 'ABSPATH' ) ) {
	exit;
}

const COMMENTATOR_BRIDGE_VERSION   = '1.4.0';
const COMMENTATOR_BRIDGE_NAMESPACE = 'commentator/v1';

/** Wie viele Kommentare eine Anfrage an /empty höchstens löscht. */
const COMMENTATOR_BRIDGE_EMPTY_BATCH = 200;

/** Hoechstzahl der Teammitglieder, die /team zurueckgibt. */
const COMMENTATOR_BRIDGE_TEAM_LIMIT = 200;

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

	register_rest_route(
		COMMENTATOR_BRIDGE_NAMESPACE,
		'/team',
		array(
			'methods'             => WP_REST_Server::READABLE,
			'callback'            => 'commentator_bridge_team',
			'permission_callback' => 'commentator_bridge_can_moderate',
		)
	);

	register_rest_route(
		COMMENTATOR_BRIDGE_NAMESPACE,
		'/empty',
		array(
			'methods'             => WP_REST_Server::CREATABLE,
			'callback'            => 'commentator_bridge_empty',
			'permission_callback' => 'commentator_bridge_can_moderate',
			'args'                => array(
				'status' => array(
					'required'          => true,
					'type'              => 'string',
					'enum'              => array( 'spam', 'trash' ),
					'sanitize_callback' => 'sanitize_key',
				),
			),
		)
	);

	register_rest_route(
		COMMENTATOR_BRIDGE_NAMESPACE,
		'/blocklist',
		array(
			array(
				'methods'             => WP_REST_Server::READABLE,
				'callback'            => 'commentator_bridge_blocklist_get',
				'permission_callback' => 'commentator_bridge_can_manage_options',
			),
			array(
				'methods'             => WP_REST_Server::CREATABLE,
				'callback'            => 'commentator_bridge_blocklist_add',
				'permission_callback' => 'commentator_bridge_can_manage_options',
				'args'                => array(
					'value' => array(
						'required' => true,
						'type'     => 'string',
					),
				),
			),
			array(
				'methods'             => WP_REST_Server::DELETABLE,
				'callback'            => 'commentator_bridge_blocklist_remove',
				'permission_callback' => 'commentator_bridge_can_manage_options',
				'args'                => array(
					'value' => array(
						'required' => true,
						'type'     => 'string',
					),
				),
			),
		)
	);
}

/**
 * Die Sperrliste ist eine seitenweite Option, kein Kommentarrecht.
 *
 * Ein Redakteur darf moderieren, aber keine Optionen ändern - das gilt hier
 * genauso wie im Backend. Die App fragt die Rechte vorher ab und bietet die
 * Sperre gar nicht erst an, wenn sie nicht greifen würde.
 */
function commentator_bridge_can_manage_options(): bool {
	return current_user_can( 'manage_options' );
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

	list( $latest_id, $latest_date ) = commentator_bridge_latest( 'hold' );

	// Zusaetzlich der neueste Kommentar unabhaengig vom Status: Auf Blogs, die
	// Kommentare automatisch freischalten, gibt es nie etwas mit Status
	// 'hold' - die App wuerde dort sonst nie bemerken, dass ueberhaupt ein
	// Kommentar eingegangen ist.
	list( $latest_any_id, $latest_any_date ) = commentator_bridge_latest( 'all' );

	$response = new WP_REST_Response(
		array(
			'pending_count'               => (int) $counts->moderated,
			'latest_comment_id'           => $latest_id,
			'latest_comment_date_gmt'     => $latest_date,
			'latest_any_comment_id'       => $latest_any_id,
			'latest_any_comment_date_gmt' => $latest_any_date,
			'plugin_version'              => COMMENTATOR_BRIDGE_VERSION,
		)
	);

	// Die Antwort ist naturgemäß flüchtig und darf nicht aus einem Cache kommen.
	$response->header( 'Cache-Control', 'no-store, private' );

	return $response;
}

/**
 * Neuester Kommentar eines Status als Paar aus Kennung und Zeitpunkt.
 *
 * @param string $status Status im Sinne von WP_Comment_Query.
 * @return array{0:int,1:?string}
 */
function commentator_bridge_latest( string $status ): array {
	// Sortiert nach Kennung, nicht nach Datum: Die App vergleicht Kennungen,
	// um zu entscheiden, ob es etwas Neues gibt. Ein Kommentar mit
	// zurueckdatiertem Zeitpunkt - beim Import keine Seltenheit - waere sonst
	// der "neueste" und wuerde die Pruefung faelschlich abbrechen lassen.
	$latest = get_comments(
		array(
			'status'  => $status,
			'number'  => 1,
			'orderby' => 'comment_ID',
			'order'   => 'DESC',
			'type'    => 'comment',
		)
	);

	if ( empty( $latest ) ) {
		return array( 0, null );
	}

	$comment = $latest[0];

	return array( (int) $comment->comment_ID, mysql_to_rfc3339( $comment->comment_date_gmt ) );
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
				// Absichtlich nicht total_comments: Das zaehlt Spam mit.
				// WP_Comment_Query versteht unter 'all' genehmigt und offen,
				// und genau das listet die REST-API bei status=all auf. Eine
				// Zahl, die nicht zur zugehoerigen Liste passt, ist schlimmer
				// als keine.
				'all'     => (int) $counts->approved + (int) $counts->moderated,
			),
		)
	);

	$response->header( 'Cache-Control', 'no-store, private' );

	return $response;
}

/**
 * Wer zum Team gehört, und welche Rollen dafür überhaupt in Frage kommen.
 *
 * Die App kann das nicht selbst ermitteln: `wp/v2/users` mit `context=edit`
 * verlangt `list_users`, und das hat ein Redakteur nicht - also genau das
 * Konto, mit dem moderiert wird. Ohne diesen Endpunkt bliebe der App nur, das
 * eigene Konto zu erkennen.
 *
 * Geliefert werden nur Rollen, die Beiträge schreiben dürfen. Abonnenten
 * gehören nicht zum Team, und auf großen Blogs wären es Tausende.
 */
function commentator_bridge_team(): WP_REST_Response {
	$roles = array();

	foreach ( wp_roles()->roles as $slug => $role ) {
		$caps = isset( $role['capabilities'] ) ? $role['capabilities'] : array();
		$gehoert_dazu = ! empty( $caps['edit_posts'] ) || ! empty( $caps['moderate_comments'] );

		if ( $gehoert_dazu ) {
			$roles[] = array(
				'slug' => (string) $slug,
				'name' => translate_user_role( $role['name'] ),
			);
		}
	}

	$members = array();

	if ( ! empty( $roles ) ) {
		$users = get_users(
			array(
				'role__in' => wp_list_pluck( $roles, 'slug' ),
				'number'   => COMMENTATOR_BRIDGE_TEAM_LIMIT,
				'orderby'  => 'ID',
				'fields'   => array( 'ID' ),
			)
		);

		foreach ( $users as $user ) {
			$data      = get_userdata( $user->ID );
			$members[] = array(
				'id'    => (int) $user->ID,
				'roles' => array_values( (array) $data->roles ),
			);
		}
	}

	$response = new WP_REST_Response(
		array(
			'roles'   => $roles,
			'members' => $members,
		)
	);

	$response->header( 'Cache-Control', 'no-store, private' );

	return $response;
}

/**
 * Leert Spam oder Papierkorb endgültig.
 *
 * Dasselbe, was die Knöpfe „Spam leeren" und „Papierkorb leeren" in der
 * Kommentarverwaltung tun. Die Kern-API kennt keine Sammellöschung; ohne
 * diesen Endpunkt bräuchte die App eine Anfrage je Kommentar.
 *
 * Bewusst in Stapeln: Bei einigen tausend Spam-Kommentaren liefe eine einzige
 * Anfrage in den Zeitablauf des Webservers. Die Antwort nennt deshalb, wie
 * viele noch übrig sind, damit die App weitermachen kann.
 */
function commentator_bridge_empty( WP_REST_Request $request ): WP_REST_Response {
	$status = $request->get_param( 'status' );

	$ids = get_comments(
		array(
			'status' => $status,
			'number' => COMMENTATOR_BRIDGE_EMPTY_BATCH,
			'fields' => 'ids',
		)
	);

	$deleted = 0;
	foreach ( $ids as $id ) {
		// true = endgültig, nicht erneut in den Papierkorb verschieben.
		if ( wp_delete_comment( (int) $id, true ) ) {
			++$deleted;
		}
	}

	$counts    = wp_count_comments();
	$remaining = 'spam' === $status ? (int) $counts->spam : (int) $counts->trash;

	$response = new WP_REST_Response(
		array(
			'deleted'   => $deleted,
			'remaining' => $remaining,
		)
	);
	$response->header( 'Cache-Control', 'no-store, private' );

	return $response;
}

/**
 * Liest die Sperrliste aus `disallowed_keys`.
 *
 * WordPress legt sie als Text mit einem Eintrag je Zeile ab. Ein Kommentar,
 * der einen dieser Einträge enthält - in Name, Adresse, Text, URL oder
 * IP -, landet direkt im Papierkorb.
 */
function commentator_bridge_blocklist_get(): WP_REST_Response {
	$response = new WP_REST_Response( array( 'entries' => commentator_bridge_blocklist_entries() ) );
	$response->header( 'Cache-Control', 'no-store, private' );

	return $response;
}

function commentator_bridge_blocklist_add( WP_REST_Request $request ) {
	$value = trim( (string) $request->get_param( 'value' ) );

	// Ein leerer Eintrag würde jeden Kommentar treffen - der Blog wäre für
	// Kommentare praktisch geschlossen, ohne dass jemand wüsste warum.
	if ( '' === $value ) {
		return new WP_Error(
			'commentator_blocklist_empty',
			__( 'Ein leerer Eintrag ist nicht zulässig.', 'commentator-bridge' ),
			array( 'status' => 400 )
		);
	}

	$entries = commentator_bridge_blocklist_entries();
	if ( ! in_array( $value, $entries, true ) ) {
		$entries[] = $value;
		commentator_bridge_blocklist_save( $entries );
	}

	return commentator_bridge_blocklist_get();
}

function commentator_bridge_blocklist_remove( WP_REST_Request $request ): WP_REST_Response {
	$value   = trim( (string) $request->get_param( 'value' ) );
	$entries = commentator_bridge_blocklist_entries();

	commentator_bridge_blocklist_save(
		array_values(
			array_filter(
				$entries,
				static function ( $entry ) use ( $value ) {
					return $entry !== $value;
				}
			)
		)
	);

	return commentator_bridge_blocklist_get();
}

/**
 * @return string[]
 */
function commentator_bridge_blocklist_entries(): array {
	$raw = (string) get_option( 'disallowed_keys', '' );

	return array_values(
		array_filter(
			array_map( 'trim', preg_split( '/\r\n|\r|\n/', $raw ) ?: array() ),
			static function ( $entry ) {
				return '' !== $entry;
			}
		)
	);
}

/**
 * @param string[] $entries
 */
function commentator_bridge_blocklist_save( array $entries ): void {
	update_option( 'disallowed_keys', implode( "\n", $entries ) );
}
