(ns fox-goose-bag-of-corn.puzzle)

(def start-pos [[[:fox :goose :corn :you] [:boat] []]])

;; We have re-defined some concepts from the initial challenge to make it easier to express the logic.
;; :you is unique in that it is both a location and an entity.
;; That makes it easy to express the concept of "picking up" an entity.
;; Under these circumnstances we don't actually need a boat, since the boat is always where you are.

;; ----------
;; Basic Definitions
;; ----------

;; A set of all locations
(def locations #{:loc/origin
                 :loc/dest})

;; A set of all entities
(def entities #{:entity/fox
                :entity/goose
                :entity/grain
                :entity/you})
;; A set of all entities which can be picked up
;; Every entity except :you
(def movable-entities (disj entities :entity/you))

;; A set of sets containing elements which cannot be alone together
(def unsafe-combos #{#{:entity/fox :entity/goose}
                     #{:entity/goose :entity/grain}})

;; The initial state
;; All entities start at :loc/origin
(def initial-state {:locations (zipmap entities (repeat :loc/origin))})
(def r-end-state {:locations (zipmap entities (repeat :loc/dest))})

;; ----------
;; Utility functions
;; ----------

(defn where?
  "returns current location of an entity"
  [state entity]
  (get-in state [:locations entity]))
#_(where? initial-state :entity/grain)
  ;; => :loc/origin
#_(where? initial-state :entity/you)
;; => :loc/origin

(defn together?
  "Are all the entities in the same location?
   Returns the location if so, null otherwise"
  [state entities]
  (if (apply = (map #(where? state %) entities))
    (where? state (first entities))
    nil))
#_(together? initial-state [:entity/you :entity/goose]) 
#_(together? r-state-moved-with-goose [:entity/goose :entity/grain])

(defn with-you?
  "returns true if the entity is in the same location as :entity/you"
  [state entity]
  (boolean (together? state [entity :entity/you])))
#_(with-you? initial-state :entity/grain)
  ;; => true
#_(with-you? initial-state :entity/foo)
  ;; => false

(defn is-at?
  "returns if an entity is at the specified location"
  [state entity loc]
  (= (where? state entity) loc))
#_(is-at? initial-state :entity/you :loc/origin)
#_(is-at? initial-state :entity/you :loc/dest)

(defn not-at?
  "returns true if an entity is not at the specified location"
  [state entity loc]
  (not (is-at? state entity loc)))
#_(not-at? initial-state :entity/you :loc/origin)
#_(not-at? initial-state :entity/you :loc/dest)

(defn in-you?
  "returns true if the entity is in :loc/you"
  [state entity]
  (is-at? state entity :loc/you))
#_(in-you? initial-state :entity/grain)
  ;; => false
#_(in-you? (assoc-in initial-state [:locations :entity/goose] :loc/you) :entity/goose)
  ;; => true

(defn your-loc
  "returns your current location"
  [state]
  (where? state :entity/you))
#_(your-loc initial-state)

(defn in-loc
  "returns the elements which are in the specified location"
  [state loc]
  (filter #(is-at? state % loc) entities))
#_(in-loc initial-state :loc/origin)
#_(in-loc initial-state :loc/dest)

(defn opposite-side
  [loc]
  (if (= :loc/origin loc)
    :loc/dest
    :loc/origin))
#_(opposite-side :loc/origin)
#_(opposite-side :loc/dest)

(defn unsupervised?
  "return true if a location is unsupervised by :entity/you"
  [state loc]
  (not-at? state :entity/you loc))
#_(unsupervised? initial-state :loc/origin)
#_(unsupervised? initial-state :loc/dest)


;; ----------
;; Functions to make changes
;; ----------


(defn move
  "returns the new state caused by moving an entity to the specified location
   there is no validation"
  [state entity new-loc]
  (assoc-in state [:locations entity] new-loc))
#_(move initial-state :entity/goose :loc/you)

;; ----------
;; Rules definitions
;; ----------

;; All the possible actions, with enumerated arguments so they can be auto-generated
;; Actions should return nil when they wouldn't make sense, like picking up something on the other shore
(def actions {:pick-up {:args movable-entities
                        :validate (fn [state [_ entity]]
                                    (with-you? state entity))
                        :handler  (fn [state [_ entity]]
                                    (move state entity :loc/you))}

              :put-down {:args movable-entities
                         :validate (fn [state [_ entity]]
                                     (in-you? state entity))
                         :handler (fn [state [_ entity]]
                                    (move state entity (your-loc state)))}

              :move-you {:args [nil]
                         :validate (constantly true)
                         :handler (fn [state _]
                                    (move state :entity/you (opposite-side (your-loc state))))}}

;; :pick-up
  #_(validate-action initial-state [:pick-up :entity/goose]))
#_(apply-action initial-state [:pick-up :entity/goose])

;; :put-down
#_(validate-action initial-state [:put-down :entity/goose])
#_(apply-action (move initial-state :entity/goose :loc/you) [:put-down :entity/goose])

;; :move-you
#_(validate-action initial-state [:move-you])
#_(apply-action initial-state [:move-you])


;; There are three kinds of validations
;; :state validations, which look at a particular state to see if it makes sense
;; :statelog validations, which look at a state to see if it makes sense in the context of previous states
;; per-action validations (defined in the actions map) which look to see if a particular action makes sense
;; in context of the current state.


(def validations {:state
                  {;; Animals are not allowed to eat each other
                   :no-eating (fn [state]
                                (not-any? (fn [combo]
                                            (if-let [common-loc (together? state combo)]
                                              (unsupervised? state common-loc))) unsafe-combos))
                   :max-carry (fn [state]
                                (<= (count (in-loc state :loc/you)) 1))}

                  :statelog
                  {;; States are not allowed to repeat
                   :no-duplicates (fn [new-state previous-states]
                                    (not-any? #(= new-state %) previous-states))}})
#_(validate-state initial-state)
#_(together? r-state-moved-with-goose #{:entity/goose :entity/grain})

(defn is-complete?
  "returns true if we have reached our desired state
   in this case it's when all entites are together in :loc/dest"
  [state]
  (= :loc/dest (together? state entities)))

#_(is-complete? initial-state)
#_(is-complete? r-end-state)

;; ----------
;; Functions to evaluate the rules
;; ----------

(defn get-action
  "Get an action"
  [action-name]
  (get actions action-name))
#_(get-action :pick-up)

(defn get-handler
  "Get the handler for an action"
  [action-name]
  (:handler (get-action action-name)))
#_(get-handler :pick-up)

(defn get-validate
  "Get the validator for an action"
  [action-name]
  (:validate (get-action action-name)))
#_(get-validator :pick-up)

(defn validate-action
  "Run the per-action validator for the given action, state, and args"
  [state [action-name arg]]
  ((get-validate action-name) state [action-name arg]))
#_(validate-action initial-state [:pick-up :entity/goose])

(defn apply-action
  "apply an action to the state (no validation)"
  [state [action-name arg]]
  ((get-handler action-name) state [action-name arg]))
#_(apply-action initial-state [:pick-up :entity/goose])
#_(apply-action initial-state [:move-me nil])

(defn validate-state
  "check if the current state makes sense"
  [state]
  (every? #(% state) (vals (:state validations))))
#_(validate-state initial-state)
(def r-state-moved-with-goose {:locations
                               {:entity/grain :loc/origin,
                                :entity/you :loc/dest,
                                :entity/fox :loc/origin,
                                :entity/goose :loc/you}})
#_(validate-state r-state-moved-with-goose)

(defn validate-state-log
  "check if the sequence of states up till now make sense"
  [new-state previous-states]
  (every? #(% new-state previous-states) (vals (:statelog validations))))
;; Just for testing
(def picked-up-state (apply-action initial-state [:pick-up :entity/goose]))
#_(validate-state-log picked-up-state [initial-state])
#_(validate-state-log picked-up-state [initial-state picked-up-state])

(defn expand-action-params
  "Given an action, return all the possible action+argument tuples"
  [action]
  (map (fn [arg] [action arg])
       (get-in actions [action :args])))
#_(expand-action-params :pick-up)

;; A list of all possible actions
(def all-actions (reduce (fn [coll a]
                           (into coll (expand-action a)))
                         [] (keys actions)))

(defn valid-actions
  "given a starting point, return a collection of valid actions
  and their resulting states.  This can be called repeatedly until termination."
  [{:keys [actions state-log current-state]}]
  (let [xform (comp (filter #(validate-action current-state %))
                    (map #(let [new-state (apply-action current-state %)]
                            {:actions (conj actions %)
                             :state-log (conj state-log current-state)
                             :current-state new-state}))
                    (filter #(validate-state (:current-state %)))
                    (filter #(validate-state-log (:current-state %) (:state-log %)))
                    (map #(assoc % :is-complete (is-complete? (:current-state %)))))]
    (into [] xform all-actions)))

;; debugging only
(def r-valid-start {:actions []
                    :state-log []
                    :current-state initial-state})
(def r-valid-conts (valid-actions r-valid-start))
(def r-first-cont (first r-valid-conts))
#_(valid-actions r-first-cont)
#_(mapcat valid-actions r-valid-conts)

;; (def r-conts (atom [r-valid-start]))
;; (mapcat )

;; ----------
;; Search Strategy
;; ----------
(def initial-search-state [{:actions [] :state-log [] :current-state initial-state}])

(defn cross-river []
  (loop [paths initial-search-state]
    (let [conts (mapcat valid-actions paths)]
      (if (some :is-complete conts)
        (:actions (first (filter :is-complete conts)))
        (recur conts)))))
(cross-river)

(defn river-crossing-plan []
  start-pos)
