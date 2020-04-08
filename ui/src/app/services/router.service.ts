import {EventEmitter, Injectable} from '@angular/core';

@Injectable({
  providedIn: 'root'
})
export class RouterService {

  currentRoute: EventEmitter<any> = new EventEmitter<any>();

  constructor() {}

  show(componentClass: any): void {
    this.currentRoute.emit(componentClass);
  }
}
