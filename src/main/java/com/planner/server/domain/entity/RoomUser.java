package com.planner.server.domain.entity;

import com.sun.istack.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class RoomUser {

    @Id
    @NotNull
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @Setter
    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @Setter
    @ManyToOne
    @JoinColumn(name = "study_room_id")
    private StudyRoom studyRoom;

}
