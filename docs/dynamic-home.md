# Dynamic Home shelves

Home selects at most five dynamic Shelves from 27 families. The device's local
date and time, actual viewing history, and verified TMDB metadata determine
eligibility. Qwen3-Embedding-0.6B ranks theme fit and profile taste over a broader
background-built catalog; it does not invent Titles or decide calendar dates.
Each Shelf needs at least six eligible Titles. Discovery shelves deduplicate
Titles, honor dislikes and existing release rules, and exclude watched/library
Titles. Trending Now remains first and owns the Billboard.
Dynamic Movie shelves require a verified runtime of at least 45 minutes;
shorts do not fill movie-night or bedtime shelves. Weak theme matches are omitted
even when they match a profile's general taste.

| Shelf | Eligibility |
| --- | --- |
| Friday Night Movie Night | Friday from 17:00; entertaining action, adventure, comedy, sci-fi or fantasy Movies. |
| Saturday Double Feature | Saturday from noon; Movies ranked for cinematic storytelling and variety. |
| Sunday Comfort Watches | Sunday from 16:00; lighter comforting Movies, excluding horror/thrillers. |
| After a Long Day | Weekdays from 17:00; lighter comedies, romance, family and adventure Movies. |
| Done Before Bed | From 17:00; verified runtime from 45 to under 100 minutes. |
| Late-Night Thrillers | From 22:00 to 03:00; suspense, mystery and crime Movies. |
| Weekend Binge | Friday evening through Sunday; short Shows with verified total episode runtime up to 12 hours. |
| Halloween Movie Night | October; horror or explicit supernatural/Halloween subjects. |
| Spooky, Not Scary | October; family Movies with spooky subjects and no horror genre; Christmas-only stories are excluded. |
| Christmas Movies | November 20 through December 25; explicit Christmas, Santa or nativity subjects. |
| Cozy Winter Watches | December through February; winter settings without horror/thrillers. |
| New Year, Fresh Start | December 26 through January 7; fresh starts and second chances. |
| Valentine’s Movie Night | February 7–14; romance Movies. |
| Summer Adventure | June through August; vacations, road trips and outdoor journeys. |
| Thanksgiving Together | Saturday through Canadian Thanksgiving Monday; family gatherings, food and homecoming. |
| Start a New Series | At most two recently active Shows; unstarted scripted Shows or miniseries. |
| Your Next Obsession | A recently completed, verified Show finale with substantial actual episode viewing. |
| One Season and Done | Weekends, or repeated drama interest; ended one-season miniseries. |
| More From [Director] | Repeated positive viewing of their Movies; verified director credit. |
| Starring [Actor] | Repeated positive viewing of their Titles; verified cast membership. |
| Hidden Gems for You | At least three meaningful positive Titles; vote-backed well-rated, less-popular Titles. |
| A Change of Pace | At least three recent watches concentrated in one genre; other genres. |
| Back to the ’90s | Repeated interest in 1990s Titles; verified 1990–1999 release. |
| Twists and Turns | Repeated mystery/thriller interest; mystery, thriller and crime Titles. |
| Worlds to Get Lost In | Repeated sci-fi/fantasy interest; those genres. |
| Something to Make You Laugh | Repeated comedy interest; comedies. |
| Based on a True Story | Repeated interest in verified true-story/biographical subjects; matching keywords. |

Seasonal and time-specific Shelves take priority, with stable daily rotation
among equally relevant themes. Credits use real person names and stable IDs.
Personal recommendations remain available alongside dynamic Shelves.

Home displays its cached snapshot immediately. Calendar eligibility refreshes
at local time boundaries; catalog warmup retries in the background. Updates
wait while a poster Shelf has focus, preserving each Shelf's independent scroll.
Expired themes are removed at the next safe Home visit. No focus gesture starts
model inference or catalog acquisition.

The dedicated TV GPU service runs independently of music AI. See
[server setup and wire contract](../server/README.md#personalized-discovery) and
[model research](dynamic-home-model-research.md) for deployment and evaluation.
