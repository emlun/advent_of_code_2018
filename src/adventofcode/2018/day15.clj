(ns adventofcode.2018.day15
  (:require clojure.string
            [clojure.spec.alpha :as spec]))

(def examples
  [
   {:map "#######\n#.G...#\n#...EG#\n#.#.#G#\n#..G#E#\n#.....#\n#######"
    :rounds 47, :winner :goblins, :hp 590, :outcome 27730}
   {:map "#######\n#G..#E#\n#E#E.E#\n#G.##.#\n#...#E#\n#...E.#\n#######"
    :rounds 37, :winner :elves, :hp 982, :outcome 36334}
   {:map "#######\n#E..EG#\n#.#G.E#\n#E.##E#\n#G..#.#\n#..E#.#\n#######"
    :rounds 46, :winner :elves, :hp 859, :outcome 39514}
   {:map "#######\n#E.G#.#\n#.#G..#\n#G.#.G#\n#G..#.#\n#...E.#\n#######"
    :rounds 35, :winner :goblins, :hp 793, :outcome 27755}
   {:map "#######\n#.E...#\n#.#..G#\n#.###.#\n#E#G#G#\n#...#G#\n#######"
    :rounds 54, :winner :goblins, :hp 536, :outcome 28944}
   {:map "#########\n#G......#\n#.E.#...#\n#..##..G#\n#...##..#\n#...#...#\n#.G...G.#\n#.....G.#\n#########"
    :rounds 20, :winner :goblins, :hp 937, :outcome 18746}
   ])

(defn format-example [s]
  (->> s
       (clojure.string/split-lines)
       (take 7)
       (map #(take 7 %))
       (map clojure.string/join)
       (clojure.string/join "\n")
       ))

(defn append-cell [state ch]
  (update state :map (fn [m]
                       (update m (dec (count m)) #(conj % ch)))))

(defn append-unit [state ch]
  (as-> state $
    (update $ :units (fn [units]
                       (conj units {:type ch
                                    :pos [(dec (count (:map state))) (count (last (:map state)))]
                                    :hp 200
                                    :power 3
                                    })
                       ))
    (append-cell $ \.)
    ))

(defn parse-state [lines]
  (as-> lines $
       (reduce (fn [state line]
                 (reduce (fn [state ch]
                           (case ch
                             (\E \G) (append-unit state ch)
                             (append-cell state ch)
                             ))
                         (update state :map (fn [m] (conj m [])))
                         line
                         ))
               {
                :units []
                :moved-units []
                :map []
                :rounds 0
                }
               $
               )
       (update $ :units #(apply list %))
       ))

(defn parse-example [i]
  (parse-state (clojure.string/split-lines (:map (examples i)))))

(defn format-map [map]
  (->> map
       (map-indexed (fn [i line]
                      (format "%3d %s" i (clojure.string/join line))))
       (clojure.string/join \newline)
       ))

(defn place-units [state]
  (reduce (fn [lines {[y x] :pos type :type}]
            (update-in lines [y x] (fn [_] type))
            )
          (:map state)
          (concat (:moved-units state) (:units state))
          ))

(defn format-state [state]
  (->> state
       (place-units)
       (format-map)
       ))

(defn print-state [state]
  (println "Round" (:rounds state))
  (println (format-state state))
  (println "Units:")
  (doseq [unit (:moved-units state)]
    (println unit))
  (println (first (:units state)) " <--")
  (doseq [unit (rest (:units state))]
    (println unit))
  )

(defn vec-add [& vectors]
  (apply mapv + vectors))
(defn vec-sub [& vectors]
  (apply mapv - vectors))

(defn adjacent [pos]
  [(vec-add pos [-1 0])
   (vec-add pos [0 -1])
   (vec-add pos [0 1])
   (vec-add pos [1 0])
   ])

(defn unoccupied [map poss]
  (filter #(= \. (get-in map %)) poss))

(defn flood [map heads destination]
  (if (and (seq heads) (= \. (get-in map destination)))
    (let [next-n (inc (get-in map (first heads)))
          [new-map new-heads] (reduce (fn [[map new-heads] pos]
                                        (reduce (fn [[map new-heads] new-pos]
                                                  (if (= \. (get-in map new-pos))
                                                    [(assoc-in map new-pos next-n) (conj new-heads new-pos)]
                                                    [map new-heads]
                                                    ))
                                                [map new-heads]
                                                (adjacent pos)
                                                ))
                                      [map []]
                                      heads
                                      )
          ]
      (recur new-map new-heads destination)
      )
    [map (get-in map destination)]
    ))

(defn first-step [flood-map destination]
  (let [step (get-in flood-map destination)]
    (if (= 1 step)
      destination
      (recur flood-map
             (->> destination
                  (adjacent)
                  (filter #(= (dec step) (get-in flood-map %)))
                  (first)
                  )
             )
      )))

(defn navigate [state start-pos target-pos]
  (let [[flood-map min-steps] (flood (-> state
                                          (place-units)
                                          (assoc-in start-pos 0)
                                          (assoc-in target-pos \.)
                                          )
                                      [start-pos]
                                      target-pos
                                      )
        reachable (not= \. min-steps)
        ]
    (if reachable [(first-step flood-map target-pos) min-steps])))

(defn print-navigation [state]
  (let [start-pos (:pos (first (:units state)))]
    (as-> state $
      (place-units $)
      (assoc-in $ start-pos 0)
      (assoc-in $ [0 0] \.)
      (flood $ [start-pos] [0 0])
      (first $)
      (map (fn [row] (map (fn [c] (if (number? c) (mod c 10) c)) row)) $)
      (format-map $)
      (println $)
      )))

(defn abs [x] (if (< x 0) (- x) x))

(defn unit-dist [u1 u2]
  (reduce + (map abs (vec-sub (:pos u1) (:pos u2)))))

(defn choose-step [state]
  (let [unit (first (:units state))
        map-with-units (place-units state)
        ]
    (->> state
         (enemies)
         (mapcat (fn [unit] (unoccupied map-with-units (adjacent (:pos unit)))))
         (map (fn [dest] [dest (navigate state (:pos unit) dest)]))
         (filter #(not (nil? (second %))))
         (sort-by (fn [[[d e] [[a b] c]]] [c d e a b]))
         (first)
         (second)
         (first)
         )))

(defn all-units [state]
  (concat (:units state) (:moved-units state)))

(defn other-units [state]
  (concat (pop (:units state)) (:moved-units state)))

(defn enemies [state]
  (filter #(not= (:type (first (:units state))) (:type %))
          (other-units state)))

(defn unit-at [state pos]
  (->> state
       (all-units)
       (filter #(= pos (:pos %)))
       (first)
       ))

(defn can-attack [state]
  (let [unit (first (:units state))
        ]
    (->> unit
         (:pos)
         (adjacent)
         (filter (set (map :pos (enemies state))))
         (sort)
         )))

(defn damage-if-target [pos power unit]
  (if (= pos (:pos unit))
    (update unit :hp #(- % power))
    unit
    ))

(defn attack [state target-pos]
  (let [unit (first (:units state))
        power (:power unit)
        ]
    (as-> state $
      (update $ :units (fn [units]
                         (apply list (map #(damage-if-target target-pos power %) units))))
      (update $ :moved-units (fn [units]
                               (mapv #(damage-if-target target-pos power %) units)))
      (update $ :units (fn [units]
                         (apply list (filter #(> (:hp %) 0) units))))
      (update $ :moved-units (fn [units]
                         (apply vector (filter #(> (:hp %) 0) units))))
      )))

(defn shift-unit [state f]
  (assoc state
         :units (pop (:units state))
         :moved-units (conj (:moved-units state) (f (first (:units state))))
         )
  )

(defn attack-after-move [state]
  (let [possible-attacks (can-attack state)
        ]
    (if (seq possible-attacks)
      (attack state (first (sort-by #(:hp (unit-at state %)) possible-attacks)))
      state
      )
    ))

(defn move-unit [state]
  (let [unit (first (:units state))
        possible-attacks (can-attack state)
        ]
    (if (seq possible-attacks)
      (as-> state $
        (attack $ (first (sort-by #(:hp (unit-at state %)) possible-attacks)))
        (shift-unit $ identity)
        )
      (if-let [chosen-step (choose-step state)
               ]
        (as-> state $
          (update $ :units (fn [units] (apply list (assoc (first units) :pos chosen-step) (pop units))))
          (attack-after-move $)
          (shift-unit $ identity)
          )
        (shift-unit state identity)
        )
      )
    ))

(defn step [state]
  (if (seq (:units state))
    (as-> state $
      (move-unit $)
      (if (seq (:units $))
        $
        (assoc $
               :rounds (inc (:rounds $))
               :units (apply list (sort-by :pos (:moved-units $)))
               :moved-units []
               )
        ))))

(defn victory [state]
  (->> state
    (all-units)
    (map :type)
    (set)
    (count)
    (= 1)
    ))

(defn hpsum [state]
  (reduce + (map :hp (all-units state))))

(defn outcome [state]
  (* (:rounds state) (hpsum state)))

(defn flip [[y x]]
  [x y])

(defn finish [states]
  (->> (last states)
       (iterate step)
       (map (fn [s] (do (print-state s) s)))
       (reductions conj [])
       (filter #(victory (last %)))
       (first)
       (concat states)
       (apply vector)
       ))

(defn solve-a [lines]
  (->> lines
       (parse-state)
       (vector)
       (finish)
       (last)
       (outcome)
       ))

(defn solve-b [lines] ())

(defn run [input-lines & args]
  {:A (solve-a input-lines)
   :B (solve-b input-lines)
   }
)

(defn day-lines [] (adventofcode.2018.core/day-lines 15))
(def states [(parse-example 0)])
(defn show-state [] (print-state (last states)))
(defn start-day-lines [] (def states [(parse-state (day-lines))]) (show-state))
(defn start-example [i] (def states [(parse-example i)]) (show-state))
(defn n [] (def states (conj states (step (last states)))) (show-state))
(defn p [] (def states (pop states)) (show-state))