"""Calendar and factual eligibility for Home themes; no network or profile storage.

Calendar decisions receive an aware datetime in the device's local timezone. The
embedding scorer handles mood; this module handles facts that similarity cannot
establish (holidays, runtime, credits, and complete limited series).
"""
from collections import Counter
from dataclasses import dataclass, replace
from datetime import datetime, timedelta
import hashlib
import re


@dataclass(frozen=True)
class Theme:
    id: str
    title: str
    query: str
    priority: int = 30
    person_id: int | None = None


_DEFINITIONS = (
    Theme('friday-movie-night', 'Friday Night Movie Night', 'Crowd-pleasing entertaining movies with adventure, spectacle, excitement and a satisfying story.', 90),
    Theme('saturday-double-feature', 'Saturday Double Feature', 'Engaging movies for a Saturday double feature, with cinematic storytelling, memorable characters and complementary themes.', 75),
    Theme('sunday-comfort', 'Sunday Comfort Watches', 'Warm comforting gentle movies, light comedy, friendship, hopeful endings and familiar adventures.', 90),
    Theme('after-long-day', 'After a Long Day', 'Easygoing lighthearted movies and comedies to unwind after work; low-stress plots and warmth.', 65),
    Theme('done-before-bed', 'Done Before Bed', 'Satisfying short movies with concise storytelling, entertaining plots and a complete story.', 70),
    Theme('late-night-thrillers', 'Late-Night Thrillers', 'Atmospheric suspense movies, crime noir, mysteries, psychological tension and unexpected twists.', 90),
    Theme('weekend-binge', 'Weekend Binge', 'Compelling short television series with a continuous story, intriguing characters, suspense and momentum.', 70),
    Theme('halloween', 'Halloween Movie Night', 'Halloween movies, ghosts, monsters, supernatural horror and eerie haunted stories.', 100),
    Theme('spooky-not-scary', 'Spooky, Not Scary', 'Playful spooky Halloween movies with friendly ghosts, whimsical monsters, magic and family-friendly supernatural adventures.', 92),
    Theme('christmas', 'Christmas Movies', 'Christmas celebrations, festive traditions, holiday romance, Santa Claus and Christmas family reunions.', 100),
    Theme('cozy-winter', 'Cozy Winter Watches', 'Cozy winter movies with snowy settings, warm friendships, gentle romance and comforting mysteries.', 65),
    Theme('new-year', 'New Year, Fresh Start', 'Hopeful movies about fresh starts, reinvention, second chances, personal growth and new beginnings.', 100),
    Theme('valentines', 'Valentine’s Movie Night', 'Romantic movies about falling in love, relationships, heartfelt romance and romantic comedy.', 100),
    Theme('summer-adventure', 'Summer Adventure', 'Summer vacations, road trips, travel, outdoor adventures and memorable journeys.', 65),
    Theme('thanksgiving', 'Thanksgiving Together', 'Warm movies about family reunions, homecoming, food, shared meals and holiday gatherings.', 100),
    Theme('start-new-series', 'Start a New Series', 'Engaging television series with a strong first season, memorable characters and an absorbing story.', 60),
    Theme('next-obsession', 'Your Next Obsession', 'An absorbing new television series with compelling characters and an intriguing continuous story.', 80),
    Theme('one-season-done', 'One Season and Done', 'A complete limited television series with one season and a satisfying self-contained story.', 60),
    Theme('more-director', 'More From [Director]', 'Movies by a favorite director, with their characteristic storytelling and style.'),
    Theme('starring-actor', 'Starring [Actor]', 'Movies and television series starring a favorite actor.'),
    Theme('hidden-gems', 'Hidden Gems for You', 'Acclaimed overlooked movies and television series with strong storytelling, originality and distinctive characters.', 55),
    Theme('change-pace', 'A Change of Pace', 'Fresh engaging movies and television series exploring different genres, tones and stories.'),
    Theme('back-90s', 'Back to the ’90s', 'Memorable movies and television series from the 1990s with distinctive characters and stories.'),
    Theme('twists-turns', 'Twists and Turns', 'Mysteries, investigations, conspiracies and psychological thrillers with unexpected plot twists.'),
    Theme('worlds', 'Worlds to Get Lost In', 'Immersive fantasy and science fiction worlds, imaginative settings, epic journeys and expansive adventures.'),
    Theme('make-laugh', 'Something to Make You Laugh', 'Funny comedies with witty dialogue, absurd situations, humorous characters and playful storytelling.'),
    Theme('true-story', 'Based on a True Story', 'Dramatizations of real events, true stories, biographies and historical lives.'),
)
_BY_ID = {theme.id: theme for theme in _DEFINITIONS}


def theme_definitions():
    return list(_DEFINITIONS)


def keywords(metadata):
    values = metadata.get('keywords', {})
    rows = values.get('keywords', values.get('results', [])) if isinstance(values, dict) else values
    return {str(row.get('name', '')).casefold().strip() for row in rows if isinstance(row, dict)}


def genres(metadata):
    return {int(row['id']) for row in metadata.get('genres', []) if isinstance(row, dict) and row.get('id')}


def _contains(metadata, terms, include_plot=False):
    """Require specific words, so e.g. 'Christmas' cannot match a snow setting."""
    content = ' '.join(keywords(metadata))
    if include_plot:
        content += ' ' + str(metadata.get('title') or metadata.get('name') or '') + ' ' + str(metadata.get('overview') or '')
    content = content.casefold()
    return any(re.search(r'\b' + re.escape(term) + r'\b', content) for term in terms)


def _true_story(metadata):
    return _contains(metadata, ('based on true story', 'based on a true story', 'based on real events',
                                'based on actual events', 'biography', 'biographical'))


def _runtime(metadata):
    value = metadata.get('runtime')
    return value if isinstance(value, (int, float)) and value > 0 else None


def _limited(metadata):
    return (metadata.get('number_of_seasons') == 1
            and metadata.get('type') == 'Miniseries'
            and metadata.get('status') == 'Ended'
            and metadata.get('number_of_episodes', 0) > 0)


def _profile(request, metadata, now):
    now_ms = int(now.timestamp() * 1000)
    dislikes, liked = set(), set()
    latest = {}
    for row in request.get('feedback', []):
        ident = f"{row['mediaType']}-{row.get('tmdbId', row.get('id'))}"
        if ident not in latest or row.get('updatedAt', 0) >= latest[ident].get('updatedAt', 0):
            latest[ident] = row
    for ident, row in latest.items():
        (liked if row.get('value') == 'like' else dislikes).add(ident)
    seen, recent, active, completed = set(), set(), set(), set()
    for row in request.get('history', []):
        ident = f"{row['mediaType']}-{row.get('tmdbId', row.get('id'))}"
        # Keep a brief accidental play from manufacturing a taste trigger.
        meaningful = row.get('watchedMs', 0) >= 300_000 or (not row.get('watchedMs', 0) and row.get('positionMs', 0) >= 1_200_000)
        if not meaningful:
            continue
        seen.add(ident)
        timestamp = row.get('lastWatchedAt', 0)
        if now_ms - 14 * 86_400_000 <= timestamp <= now_ms + 300_000:
            recent.add(ident)
            if row['mediaType'] == 'tv':
                show_id = int(row.get('tmdbId', row.get('id')))
                show = metadata.get(ident, {})
                finale = show.get('last_episode_to_air') or {}
                duration = row.get('durationMs', 0)
                finished = (show.get('status') in ('Ended', 'Canceled')
                            and finale.get('season_number', 0) > 0
                            and finale.get('episode_number', 0) > 0
                            and row.get('season', 0) == finale['season_number']
                            and row.get('episode', 0) == finale['episode_number']
                            and duration > 0 and row.get('positionMs', 0) / duration >= 0.9
                            and row.get('latestWatchedMs', 0) >= max(300_000, duration * 0.5))
                (completed if finished else active).add(show_id)
    active -= completed
    positive = (seen | liked) - dislikes
    counts, recent_genres, directors, actors = Counter(), Counter(), Counter(), Counter()
    people = {}
    for ident in positive:
        item = metadata.get(ident)
        if not item:
            continue
        counts.update(genres(item))
        if ident in recent:
            recent_genres.update(genres(item))
        for person in item.get('credits', {}).get('crew', []):
            if ident.startswith('movie-') and person.get('job') == 'Director' and person.get('id'):
                directors[person['id']] += 1
                people[person['id']] = person.get('name', '')
        for person in item.get('credits', {}).get('cast', [])[:5]:
            if person.get('id'):
                actors[person['id']] += 1
                people[person['id']] = person.get('name', '')
    dominant = recent_genres.most_common(1)
    dominant_genre = dominant[0][0] if dominant and len(recent) >= 3 and dominant[0][1] / len(recent) >= 0.75 else None
    return dict(positive=positive, genre_counts=counts, active=active, completed=completed,
                directors=directors, actors=actors, people=people, dominant_genre=dominant_genre)


def profile_context(request, metadata, now):
    """Transient trigger evidence, usable by the ranking layer; never persisted."""
    return _profile(request, metadata, now)


def active_themes(request, metadata, now):
    if not isinstance(now, datetime) or now.tzinfo is None:
        raise ValueError('theme time must include the device local timezone')
    context = _profile(request, metadata, now)
    ids = []
    weekday, hour = now.weekday(), now.hour
    weekend = weekday == 5 or weekday == 6 or (weekday == 4 and hour >= 17)
    evening = hour >= 17
    if weekday == 4 and evening:
        ids.append('friday-movie-night')
    if weekday == 5 and hour >= 12:
        ids.append('saturday-double-feature')
    if weekday == 6 and hour >= 16:
        ids.append('sunday-comfort')
    if weekday < 5 and evening:
        ids.append('after-long-day')
    if evening:
        ids.append('done-before-bed')
    if hour >= 22 or hour < 3:
        ids.append('late-night-thrillers')
    if weekend:
        ids.extend(('weekend-binge', 'one-season-done'))
    elif context['genre_counts'].get(18, 0) >= 2:
        ids.append('one-season-done')
    if now.month == 10:
        ids.extend(('halloween', 'spooky-not-scary'))
    if (now.month == 11 and now.day >= 20) or (now.month == 12 and now.day <= 25):
        ids.append('christmas')
    if now.month in (12, 1, 2):
        ids.append('cozy-winter')
    if (now.month == 12 and now.day >= 26) or (now.month == 1 and now.day <= 7):
        ids.append('new-year')
    if now.month == 2 and 7 <= now.day <= 14:
        ids.append('valentines')
    if now.month in (6, 7, 8):
        ids.append('summer-adventure')
    # Canada's Thanksgiving: Saturday through the second Monday of October.
    first_october = now.date().replace(month=10, day=1)
    first_monday = first_october + timedelta(days=(0 - first_october.weekday()) % 7)
    thanksgiving = first_monday + timedelta(days=7)
    if thanksgiving - timedelta(days=2) <= now.date() <= thanksgiving:
        ids.append('thanksgiving')
    if len(context['active']) <= 2:
        ids.append('start-new-series')
    if context['completed']:
        ids.append('next-obsession')
    if len(context['positive']) >= 3:
        ids.append('hidden-gems')
    if context['dominant_genre'] is not None:
        ids.append('change-pace')
    if sum(1 for ident in context['positive'] if _year(metadata.get(ident, {})) in range(1990, 2000)) >= 2:
        ids.append('back-90s')
    if sum(bool(genres(metadata.get(ident, {})) & {9648, 53}) for ident in context['positive']) >= 2:
        ids.append('twists-turns')
    if sum(bool(genres(metadata.get(ident, {})) & {878, 14, 10765}) for ident in context['positive']) >= 2:
        ids.append('worlds')
    if context['genre_counts'].get(35, 0) >= 2:
        ids.append('make-laugh')
    if sum(_true_story(metadata.get(ident, {})) for ident in context['positive']) >= 2:
        ids.append('true-story')
    result = [_BY_ID[ident] for ident in ids]
    for ident, count, label, pattern in (('more-director', context['directors'], 'More From ', 'director'),
                                       ('starring-actor', context['actors'], 'Starring ', 'actor')):
        favorite = sorted(count, key=lambda person: (-count[person], person))
        if favorite and count[favorite[0]] >= 2 and context['people'].get(favorite[0]):
            person_id = favorite[0]
            name = context['people'][person_id]
            theme = _BY_ID[ident]
            result.append(replace(theme, id=f'{ident}-{person_id}', title=label + name,
                                  query=f'Movies and television featuring {pattern} {name}.', person_id=person_id))
    # Stable daily variety within each relevance level; timestamps never reshuffle a visit.
    date_seed = now.date().isoformat()
    return sorted(result, key=lambda theme: (-theme.priority,
                  hashlib.sha256((date_seed + theme.id).encode()).digest()))


def _year(metadata):
    value = metadata.get('release_date') or metadata.get('first_air_date') or ''
    try:
        return int(str(value)[:4])
    except ValueError:
        return 0


def eligible_for_theme(theme, metadata, media_type, profile_context=None):
    ident, genre = theme.id, genres(metadata)
    movie = media_type == 'movie'
    # Feature shelves should not become collections of shorts or behind-the-
    # scenes clips discovered through a performer's credits.
    if movie and (_runtime(metadata) is None or _runtime(metadata) < 45):
        return False
    if ident.startswith('more-director-'):
        return movie and any(p.get('id') == theme.person_id and p.get('job') == 'Director'
                            for p in metadata.get('credits', {}).get('crew', []))
    if ident.startswith('starring-actor-'):
        return any(p.get('id') == theme.person_id for p in metadata.get('credits', {}).get('cast', []))
    if ident in ('start-new-series', 'next-obsession'):
        return not movie and metadata.get('type') in ('Scripted', 'Miniseries')
    if ident == 'one-season-done':
        return not movie and _limited(metadata)
    if ident == 'weekend-binge':
        episodes = metadata.get('number_of_episodes', 0)
        runtimes = [v for v in metadata.get('episode_run_time', []) if isinstance(v, (int, float)) and v > 0]
        # Claim "short" only when total verified length is within a 12-hour weekend.
        return not movie and metadata.get('type') in ('Scripted', 'Miniseries') and 0 < episodes <= 12 and bool(runtimes) and episodes * max(runtimes) <= 720
    if ident == 'done-before-bed':
        runtime = _runtime(metadata)
        return movie and runtime is not None and runtime < 100
    if ident == 'halloween':
        return movie and (27 in genre or _contains(metadata, ('halloween', 'ghost', 'haunted house', 'witch', 'vampire', 'werewolf', 'monster', 'supernatural'), True))
    if ident == 'spooky-not-scary':
        # Family genre is factual; absence of horror alone doesn't imply child-friendly.
        christmas = _contains(metadata, ('christmas', 'santa claus', 'nativity'), True)
        halloween = _contains(metadata, ('halloween',), True)
        if christmas and not halloween:
            return False
        return movie and 10751 in genre and 27 not in genre and _contains(metadata, ('halloween', 'ghost', 'witch', 'monster', 'supernatural', 'magic'), True)
    if ident == 'christmas':
        return movie and _contains(metadata, ('christmas', 'santa claus', 'nativity'), True)
    if ident == 'cozy-winter':
        return movie and 27 not in genre and 53 not in genre and _contains(metadata, ('winter', 'snow', 'snowy', 'christmas'), True)
    if ident == 'valentines':
        return movie and 10749 in genre
    if ident == 'summer-adventure':
        return movie and _contains(metadata, ('road trip', 'summer vacation', 'summer holiday', 'vacation', 'camping', 'travel', 'journey', 'outdoor adventure'), True)
    if ident == 'thanksgiving':
        return movie and _contains(metadata, ('thanksgiving', 'family reunion', 'homecoming', 'cooking', 'family dinner', 'family gathering'), True)
    if ident == 'hidden-gems':
        return metadata.get('vote_average', 0) >= 7 and metadata.get('vote_count', 0) >= 100 and metadata.get('popularity', 0) < 20
    if ident == 'back-90s':
        return 1990 <= _year(metadata) <= 1999
    if ident == 'twists-turns' or ident == 'late-night-thrillers':
        return (movie or ident == 'twists-turns') and bool(genre & {53, 9648, 80})
    if ident == 'worlds':
        return bool(genre & {878, 14, 10765})
    if ident == 'make-laugh':
        return 35 in genre
    if ident == 'true-story':
        return _true_story(metadata)
    if ident == 'change-pace':
        dominant = (profile_context or {}).get('dominant_genre')
        return dominant is not None and dominant not in genre
    if ident in ('friday-movie-night', 'saturday-double-feature', 'sunday-comfort', 'after-long-day', 'new-year'):
        if not movie:
            return False
        if ident in ('sunday-comfort', 'after-long-day'):
            light = bool(genre & {35, 10751, 10749})
            if not light and genre & {28, 10752, 80}:
                return False
            return 27 not in genre and 53 not in genre and bool(genre & {35, 10751, 10749, 12})
        if ident == 'friday-movie-night':
            return bool(genre & {28, 12, 35, 878, 14})
        return True
    return False
